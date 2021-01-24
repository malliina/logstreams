package com.malliina.logstreams.http4s

import cats.effect.IO
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
// (implicit cs: ContextShift[IO])
object LogSockets {
  private val log = AppLogger(getClass)
}

class LogSockets(
  logs: Topic[IO, LogEntryInputs],
  admins: Topic[IO, AdminEvent],
  db: LogsDatabase[IO]
) {
  private val savedEvents = logs.subscribe(100).evalMap { ins =>
    db.insert(ins.events).map { written =>
      AppLogEvents(written.rows.map(_.toEvent))
    }
  }
  private val logIncoming: Pipe[IO, WebSocketFrame, Unit] = _.evalMap {
    case Text(message, _) => IO(log.info(message))
    case f                => IO(log.debug(s"Unknown WebSocket frame: $f"))
  }

  def listener(query: StreamsQuery) = {
//    val pings = fs2.Stream.awakeEvery[IO](30.seconds)
    val filteredEvents =
      if (query.apps.isEmpty) {
        savedEvents
      } else {
        savedEvents.map { es =>
          es.filter(e => query.apps.exists(app => app.name == e.source.name.name))
        }
      }
    val toClient = fs2.Stream
      .eval(db.events(query))
      .flatMap { history =>
        fs2.Stream(history) ++ filteredEvents.map(
          _.filter(e => !history.events.exists(_.id == e.id))
        )
      }
      .filter(_.events.nonEmpty)
      .through(jsonTransform[AppLogEvents])
    WebSocketBuilder[IO].build(toClient, logIncoming)
  }

  def admin(user: UserRequest) = {
    WebSocketBuilder[IO].build(
      admins.subscribe(100).through(jsonTransform[AdminEvent]),
      logIncoming
    )
  }

  def source(user: UserRequest) = {
    val toSource = fs2.Stream.never[IO]
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
    WebSocketBuilder[IO].build(toSource, publishEvents)
  }

  private def jsonTransform[T: Writes](src: fs2.Stream[IO, T]) = src.map { t =>
    Text(Json.stringify(Json.toJson(t)))
  }
}
