package controllers

import java.time.Instant

import akka.NotUsed
import akka.actor.Props
import akka.pattern.ask
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{BroadcastHub, Flow, Keep, MergeHub, Sink, Source}
import akka.util.Timeout
import ch.qos.logback.classic.Level
import com.malliina.logstreams.db.StreamsDatabase
import com.malliina.logstreams.models._
import com.malliina.logstreams.ws.SourceManager.{AppJoined, AppLeft, GetApps}
import com.malliina.logstreams.ws._
import com.malliina.play.ActorExecution
import com.malliina.play.auth.Authenticator
import com.malliina.play.http.Proxies
import com.malliina.play.models.Username
import com.malliina.play.ws._
import play.api.libs.json.JsValue
import play.api.mvc.WebSocket
import play.api.mvc.WebSocket.MessageFlowTransformer.jsonMessageFlowTransformer

import scala.concurrent.duration.DurationInt

class SocketsBundle(listenerAuth: Authenticator[Username],
                    sourceAuth: Authenticator[Username],
                    db: StreamsDatabase,
                    deps: ActorExecution) {
  implicit val mat = deps.materializer
  implicit val ec = deps.executionContext
  val logs = listeners(LogsMediator.props(db), listenerAuth)
  val admins = listeners(LatestMediator.props(), listenerAuth)
  val database = deps.actorSystem.actorOf(DatabaseActor.props(db))
  val sourceProps = Props(new SourceMediator(logs.mediator, admins.mediator, database))
  val sources = new SourceSockets(sourceProps, sourceAuth, deps)

  // Publish-Subscribe Akka Streams
  // https://doc.akka.io/docs/akka/2.5/stream/stream-dynamic.html
  val (appsSink, logsSource) = MergeHub.source[LogEntryInputs](perProducerBufferSize = 16)
    .toMat(BroadcastHub.sink(bufferSize = 256))(Keep.both)
    .run()

  val savedEvents: Source[FrontEvent, NotUsed] = logsSource
    .mapAsync(parallelism = 10)(ins => db.insert(ins.events).map(written => AppLogEvents(written.rows.map(_.toEvent))))

  val _ = logsSource.runWith(Sink.ignore)

  implicit val listenerTransformer = jsonMessageFlowTransformer[JsValue, FrontEvent]
  implicit val adminTransformer = jsonMessageFlowTransformer[JsValue, AdminEvent]

  val (ref, pub) = Source.actorRef[AdminEvent](10, OverflowStrategy.dropHead).toMat(Sink.asPublisher(fanout = true))(Keep.both).run()
  val adminSource: Source[AdminEvent, NotUsed] = Source.fromPublisher(pub)
  val serverManager = deps.actorSystem.actorOf(SourceManager.props(ref))

  def listenerSocket = WebSocket.acceptOrResult[JsValue, FrontEvent] { rh =>
    listenerAuth.authenticate(rh).map { e =>
      e.left.map(logs.onUnauthorized(rh, _)).map { _ =>
        Flow.fromSinkAndSource(Sink.ignore, Source.fromFuture(db.events()).filter(_.events.nonEmpty).concat(savedEvents))
          .keepAlive(10.seconds, () => SimpleEvent.ping)
          .backpressureTimeout(5.seconds)
      }
    }
  }

  def adminSocket = WebSocket.acceptOrResult[JsValue, AdminEvent] { rh =>
    listenerAuth.authenticate(rh).flatMap { e =>
      implicit val timeout = Timeout(5.seconds)
      (serverManager ? GetApps).mapTo[LogSources].map { sources =>
        e.left.map(logs.onUnauthorized(rh, _)).map { _ =>
          Flow.fromSinkAndSource(Sink.ignore, Source.single(sources).concat(adminSource))
            .keepAlive(10.seconds, () => SimpleEvent.ping)
            .backpressureTimeout(5.seconds)
        }
      }
    }
  }

  def sourceSocket = WebSocket { rh =>
    sourceAuth.authenticate(rh).map { e =>
      e.left.map(sources.onUnauthorized(rh, _)).map { user =>
        val server = LogSource(AppName(user.name), Proxies.realAddress(rh))
        serverManager ! AppJoined(server)
        val transformer = jsonMessageFlowTransformer.map[LogEntryInputs, JsValue](
          json => json.validate[LogEvents].map { es =>
            LogEntryInputs(es.events.map { event =>
              LogEntryInput(
                user,
                Proxies.realAddress(rh),
                Instant.ofEpochMilli(event.timeStamp),
                event.message,
                event.loggerName,
                event.threadName,
                Level.toLevel(event.level),
                event.stackTrace
              )
            })
          }.getOrElse(throw new Exception),
          out => out
        )
        transformer.transform(Flow.fromSinkAndSource(appsSink, Source.maybe[JsValue])).watchTermination() { (_, termination) =>
          termination.foreach { _ => serverManager ! AppLeft(server) }
          NotUsed
        }
      }
    }
  }

  //  implicit val ec = deps.executionContext
  //  val event = AppLogEvent(LogSource(AppName("test"), "remote"), TestData.dummyEvent("jee"))
  //  val errorEvent = AppLogEvent(LogSource(AppName("test"), "remote"), TestData.failEvent("boom"))
  //  deps.actorSystem.scheduler.schedule(1.seconds, 1.second, sources.mediator, AppLogEvents(Seq(event, errorEvent)))

  def listenerSocket2 = logs.newSocket

  def adminSocket2 = admins.newSocket

  def sourceSocket2 = sources.newSocket

  def listeners[U](props: Props, auth: Authenticator[U]): MediatorSockets[U] =
    new MediatorSockets[U](props, auth, deps)
}
