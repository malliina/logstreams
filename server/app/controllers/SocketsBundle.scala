package controllers

import java.time.Instant

import akka.NotUsed
import akka.actor.ActorRef
import akka.pattern.ask
import akka.stream.scaladsl.{BroadcastHub, Flow, Keep, MergeHub, Sink, Source}
import akka.stream.{Materializer, OverflowStrategy}
import akka.util.Timeout
import ch.qos.logback.classic.Level
import com.malliina.logstreams.Streams
import com.malliina.logstreams.Streams.onlyOnce
import com.malliina.logstreams.db.{LogsDatabase, StreamsQuery}
import com.malliina.logstreams.models._
import com.malliina.logstreams.ws.SourceManager.{AppJoined, AppLeft, GetApps}
import com.malliina.logstreams.ws._
import com.malliina.play.ActorExecution
import com.malliina.play.auth.{AuthFailure, Authenticator}
import com.malliina.play.http.Proxies
import com.malliina.values.Username
import controllers.SocketsBundle.log
import play.api.Logger
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Results.{BadRequest, Unauthorized}
import play.api.mvc.WebSocket.MessageFlowTransformer
import play.api.mvc.WebSocket.MessageFlowTransformer.jsonMessageFlowTransformer
import play.api.mvc.{RequestHeader, Result, Results, WebSocket}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContextExecutor, Future}

object SocketsBundle {
  private val log = Logger(getClass)
}

class SocketsBundle(
  listenerAuth: Authenticator[Username],
  sourceAuth: Authenticator[Username],
  db: LogsDatabase,
  deps: ActorExecution
) {
  implicit val mat: Materializer = deps.materializer
  implicit val ec: ExecutionContextExecutor = deps.executionContext

  // Publish-Subscribe Akka Streams
  // https://doc.akka.io/docs/akka/2.5/stream/stream-dynamic.html
  val (appsSink, logsSource) = MergeHub
    .source[LogEntryInputs](perProducerBufferSize = 256)
    .toMat(BroadcastHub.sink(bufferSize = 256))(Keep.both)
    .run()

  val savedEvents: Source[AppLogEvents, NotUsed] = onlyOnce(logsSource.mapAsync(parallelism = 10) {
    ins =>
      db.insert(ins.events).map(written => AppLogEvents(written.rows.map(_.toEvent)))
  })

  val _ = savedEvents.runWith(Sink.ignore)

  implicit val listenerTransformer: MessageFlowTransformer[JsValue, FrontEvent] =
    jsonMessageFlowTransformer[JsValue, FrontEvent]
  implicit val adminTransformer: MessageFlowTransformer[JsValue, AdminEvent] =
    jsonMessageFlowTransformer[JsValue, AdminEvent]

  val (ref, pub) = Streams
    .actorRef(10, OverflowStrategy.dropHead)
    .toMat(Sink.asPublisher(fanout = true))(Keep.both)
    .run()
  val adminSource: Source[AdminEvent, NotUsed] = Source.fromPublisher(pub)
  // drains admin events for situations where no admin is subscribed
  adminSource.runWith(Sink.ignore)
  val serverManager: ActorRef = deps.actorSystem.actorOf(SourceManager.props(ref))

  def listenerSocket = WebSocket.acceptOrResult[JsValue, FrontEvent] { rh =>
    auth(rh, listenerAuth) { _ =>
      Future.successful {
        StreamsQuery(rh)
          .map { query =>
            val filteredEvents =
              if (query.apps.isEmpty) {
                savedEvents
              } else {
                savedEvents.map { es =>
                  es.filter(e => query.apps.exists(app => app.name == e.source.name.name))
                }
              }
            val concatEvents: Source[AppLogEvents, NotUsed] =
              Source
                .future(db.events(query))
                .flatMapConcat(history =>
                  Source
                    .single(history.reverse)
                    .concat(filteredEvents.map(_.filter(e => !history.events.exists(_.id == e.id))))
                )
                .filter(_.events.nonEmpty)
            Flow
              .fromSinkAndSource(Sink.ignore, concatEvents)
              .keepAlive(10.seconds, () => SimpleEvent.ping)
              .backpressureTimeout(5.seconds)
          }
          .left
          .map { err =>
            log.error(s"${err.message} - $rh")
            BadRequest(err.message)
          }
      }
    }
  }

  def adminSocket = WebSocket.acceptOrResult[JsValue, AdminEvent] { rh =>
    auth(rh, listenerAuth) { _ =>
      implicit val timeout: Timeout = Timeout(5.seconds)
      (serverManager ? GetApps).mapTo[LogSources].map { sources =>
        Right {
          Flow
            .fromSinkAndSource(Sink.ignore, Source.single(sources).concat(adminSource))
            .keepAlive(10.seconds, () => SimpleEvent.ping)
            .backpressureTimeout(5.seconds)
        }
      }
    }
  }

  def sourceSocket = WebSocket { rh =>
    auth(rh, sourceAuth) { user =>
      val server = LogSource(AppName(user.name), Proxies.realAddress(rh))
      serverManager ! AppJoined(server)
      val transformer = jsonMessageFlowTransformer.map[LogEntryInputs](json =>
        json
          .validate[LogEvents]
          .map { es =>
            LogEntryInputs(es.events.map { event =>
              LogEntryInput(
                user,
                Proxies.realAddress(rh),
                Instant.ofEpochMilli(event.timestamp),
                event.message,
                event.loggerName,
                event.threadName,
                Level.toLevel(event.level),
                event.stackTrace
              )
            })
          }
          .getOrElse(throw new Exception)
      )
      val typedFlow = Flow
        .fromSinkAndSource(appsSink, Source.maybe[JsValue])
        .keepAlive(10.seconds, () => Json.toJson(SimpleEvent.ping))
      right {
        transformer.transform(typedFlow).watchTermination() { (_, termination) =>
          termination.foreach { _ =>
            serverManager ! AppLeft(server)
          }
          NotUsed
        }
      }
    }
  }

  def right[T](t: => T): Future[Right[Nothing, T]] = Future.successful(Right(t))

  def auth[T](rh: RequestHeader, impl: Authenticator[Username])(
    code: Username => Future[Either[Result, T]]
  ): Future[Either[Result, T]] =
    impl
      .authenticate(rh)
      .flatMap(e => e.fold(f => Future.successful(Left(onUnauthorized(rh, f))), u => code(u)))

  def onUnauthorized(rh: RequestHeader, failure: AuthFailure): Result = {
    log warn s"Unauthorized request $rh"
    Unauthorized
  }
}
