package com.malliina.logstreams.http4s

import cats.effect.kernel.Ref
import cats.effect.unsafe.implicits.global
import cats.effect.IO
import ch.qos.logback.classic.Level
import com.malliina.logstreams.db.{LogsDatabase, StreamsQuery}
import com.malliina.logstreams.http4s.LogSockets.log
import com.malliina.logstreams.models._
import com.malliina.util.AppLogger
import controllers.UserRequest
import fs2.Pipe
import fs2.concurrent.Topic
import io.circe.Encoder
import io.circe.parser._
import io.circe.syntax.EncoderOps
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.Text

import java.time.Instant
import scala.concurrent.duration.DurationInt

object LogSockets {
  private val log = AppLogger(getClass)
}

class LogSockets(
  logs: Topic[IO, LogEntryInputs],
  admins: Topic[IO, LogSources],
  connectedSources: Ref[IO, LogSources],
  logUpdates: Topic[IO, AppLogEvents],
  db: LogsDatabase[IO]
) {
  private val savedEvents: fs2.Stream[IO, AppLogEvents] = logs.subscribe(100).evalMap { ins =>
    db.insert(ins.events).map { written =>
      AppLogEvents(written.rows.map(_.toEvent))
    }
  }
  val publisher = savedEvents.evalMap { saved =>
    logUpdates.publish1(saved)
  }
  // saves to the database and publishes events
  publisher.compile.drain.unsafeRunAndForget()
  private val logIncoming: Pipe[IO, WebSocketFrame, Unit] = _.evalMap {
    case Text(message, _) => IO(log.info(message))
    case f                => IO(log.debug(s"Unknown WebSocket frame: $f"))
  }
  val pings = fs2.Stream.awakeEvery[IO](15.seconds).map(_ => SimpleEvent.ping)

  def listener(query: StreamsQuery) = {
    val subscription =
      logUpdates.subscribe(100).drop(1).map { es =>
        es.filter(_.event.level.int >= query.level.int)
      }
    val filteredEvents =
      if (query.apps.isEmpty) {
        subscription
      } else {
        subscription.map { es =>
          es.filter(e => query.apps.exists(app => app.name == e.source.name.name))
        }
      }
    val logEvents = fs2.Stream
      .eval(db.events(query))
      .flatMap { history =>
        fs2.Stream(history.reverse) ++ filteredEvents.map(
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
        val event = decode[LogEvents](message).fold(
          err => IO.raiseError(new JsonException(err, message)),
          es => {
            val inputs = LogEntryInputs(es.events.map { event =>
              LogEntryInput(
                user.user,
                user.address,
                Instant.ofEpochMilli(event.timestamp),
                event.message,
                event.loggerName,
                event.threadName,
                event.level,
                event.stackTrace
              )
            })
            IO.pure(inputs)
          }
        )
        event.flatMap { e => logs.publish1(e).map(_ => ()) }
      case f => IO(log.debug(s"Unknown WebSocket frame: $f"))
    }
    val logSource = LogSource(AppName(user.user.name), user.address)
    connected(logSource).flatMap { _ =>
      WebSocketBuilder[IO]
        .copy(onClose = disconnected(logSource))
        .build(
          pings.through(jsonTransform[SimpleEvent]),
          publishEvents
        )
    }
  }

  def connected(src: LogSource): IO[Unit] =
    connectedSources.updateAndGet(olds => LogSources(olds.sources :+ src)).flatMap { connecteds =>
      admins.publish1(connecteds).map(_ => ())
    }

  def disconnected(src: LogSource): IO[Unit] =
    connectedSources.updateAndGet(olds => LogSources(olds.sources.filterNot(_ == src))).flatMap {
      connecteds =>
        admins.publish1(connecteds).map(_ => ())
    }

  private def jsonTransform[T: Encoder](src: fs2.Stream[IO, T]): fs2.Stream[IO, Text] = src.map {
    t =>
      Text(t.asJson.noSpaces)
  }
}
