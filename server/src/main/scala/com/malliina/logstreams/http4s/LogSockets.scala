package com.malliina.logstreams.http4s

import cats.effect.concurrent.Ref
import cats.effect.{ContextShift, IO, Timer}
import ch.qos.logback.classic.Level
import com.malliina.logstreams.db.{LogsDatabase, StreamsQuery}
import com.malliina.logstreams.http4s.LogSockets.log
import com.malliina.logstreams.models._
import com.malliina.util.AppLogger
import controllers.UserRequest
import fs2.Pipe
import fs2.concurrent.Topic
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.Text
import play.api.libs.json.{JsError, Json, Writes}

import java.time.Instant
import scala.concurrent.duration.DurationInt

object LogSockets {
  private val log = AppLogger(getClass)
}

class LogSockets(
  logs: Topic[IO, LogEntryInputs],
  admins: Topic[IO, LogSources],
  connectedSources: Ref[IO, LogSources],
  db: LogsDatabase[IO]
)(implicit cs: ContextShift[IO], timer: Timer[IO]) {
  private val savedEvents: fs2.Stream[IO, AppLogEvents] = logs.subscribe(100).evalMap { ins =>
    db.insert(ins.events).map { written =>
      AppLogEvents(written.rows.map(_.toEvent))
    }
  }
  // drains log events for situations where no viewer is subscribed, so that they're always saved to the db either way
  savedEvents.compile.drain.unsafeRunAsyncAndForget()
  private val logIncoming: Pipe[IO, WebSocketFrame, Unit] = _.evalMap {
    case Text(message, _) => IO(log.info(message))
    case f                => IO(log.debug(s"Unknown WebSocket frame: $f"))
  }
  val pings = fs2.Stream.awakeEvery[IO](15.seconds).map(_ => SimpleEvent.ping)

  def listener(query: StreamsQuery) = {
    val filteredEvents =
      if (query.apps.isEmpty) {
        savedEvents
      } else {
        savedEvents.map { es =>
          es.filter(e => query.apps.exists(app => app.name == e.source.name.name))
        }
      }
    val logEvents = fs2.Stream
      .eval(db.events(query))
      .flatMap { history =>
        fs2.Stream(history) ++ filteredEvents.map(
          _.filter(e => !history.events.exists(_.id == e.id))
        )
      }
      .filter(_.events.nonEmpty)
    val toClient = pings
      .mergeHaltBoth(logEvents)
      .through(jsonTransform[FrontEvent])
    WebSocketBuilder[IO].build(toClient, logIncoming)
  }

  def admin(user: UserRequest) =
    WebSocketBuilder[IO].build(
      pings.mergeHaltBoth(admins.subscribe(100)).through(jsonTransform[AdminEvent]),
      logIncoming
    )

  def source(user: UserRequest) = {
    val publishEvents: Pipe[IO, WebSocketFrame, Unit] = _.evalMap {
      case Text(message, _) =>
        val event = IO(Json.parse(message)).flatMap { json =>
          json
            .validate[LogEvents]
            .map { es =>
              LogEntryInputs(es.events.map { event =>
                LogEntryInput(
                  user.user,
                  user.address,
                  Instant.ofEpochMilli(event.timestamp),
                  event.message,
                  event.loggerName,
                  event.threadName,
                  Level.toLevel(event.level),
                  event.stackTrace
                )
              })
            }
            .fold(err => IO.raiseError(new JsonException(JsError(err), json)), ok => IO.pure(ok))
        }
        event.flatMap { e => logs.publish1(e) }
      case f => IO(log.debug(s"Unknown WebSocket frame: $f"))
    }
    val logSource = LogSource(AppName(user.user.name), user.address)
    connected(logSource).flatMap { _ =>
      WebSocketBuilder[IO].build(
        pings.through(jsonTransform[SimpleEvent]),
        publishEvents,
        onClose = disconnected(logSource)
      )
    }
  }

  def connected(src: LogSource) =
    connectedSources.updateAndGet(olds => LogSources(olds.sources :+ src)).flatMap { connecteds =>
      admins.publish1(connecteds)
    }

  def disconnected(src: LogSource) =
    connectedSources.updateAndGet(olds => LogSources(olds.sources.filterNot(_ == src))).flatMap {
      connecteds =>
        admins.publish1(connecteds)
    }

  private def jsonTransform[T: Writes](src: fs2.Stream[IO, T]) = src.map { t =>
    Text(Json.stringify(Json.toJson(t)))
  }
}
