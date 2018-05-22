package controllers

import java.time.Instant

import akka.NotUsed
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
import com.malliina.play.auth.{AuthFailure, Authenticator}
import com.malliina.play.http.Proxies
import com.malliina.play.models.Username
import controllers.SocketsBundle.log
import play.api.Logger
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.WebSocket.MessageFlowTransformer.jsonMessageFlowTransformer
import play.api.mvc.{RequestHeader, Result, Results, WebSocket}

import scala.concurrent.duration.DurationInt

object SocketsBundle {
  private val log = Logger(getClass)
}

class SocketsBundle(listenerAuth: Authenticator[Username],
                    sourceAuth: Authenticator[Username],
                    db: StreamsDatabase,
                    deps: ActorExecution) {
  implicit val mat = deps.materializer
  implicit val ec = deps.executionContext

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
      e.left.map(onUnauthorized(rh, _)).map { _ =>
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
        e.left.map(onUnauthorized(rh, _)).map { _ =>
          Flow.fromSinkAndSource(Sink.ignore, Source.single(sources).concat(adminSource))
            .keepAlive(10.seconds, () => SimpleEvent.ping)
            .backpressureTimeout(5.seconds)
        }
      }
    }
  }

  def sourceSocket = WebSocket { rh =>
    sourceAuth.authenticate(rh).map { e =>
      e.left.map(onUnauthorized(rh, _)).map { user =>
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
        val typedFlow = Flow.fromSinkAndSource(appsSink, Source.maybe[JsValue])
          .keepAlive(10.seconds, () => Json.toJson(SimpleEvent.ping))
        transformer.transform(typedFlow).watchTermination() { (_, termination) =>
          termination.foreach { _ => serverManager ! AppLeft(server) }
          NotUsed
        }
      }
    }
  }

  def onUnauthorized(rh: RequestHeader, failure: AuthFailure): Result = {
    log warn s"Unauthorized request $rh"
    Results.Unauthorized
  }
}
