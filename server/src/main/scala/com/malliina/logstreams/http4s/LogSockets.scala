package com.malliina.logstreams.http4s

import cats.effect.kernel.Ref
import cats.effect.{IO, Resource}
import ch.qos.logback.classic.Level
import com.malliina.logstreams.db.{LogsDatabase, StreamsQuery, Utils}
import com.malliina.logstreams.http4s.LogSockets.log
import com.malliina.logstreams.http4s.UserRequest
import com.malliina.logstreams.models.*
import com.malliina.util.AppLogger
import fs2.concurrent.Topic
import fs2.{Pipe, Stream}
import io.circe.Encoder
import io.circe.parser.*
import io.circe.syntax.EncoderOps
import org.http4s.Response
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.Text

import java.time.Instant
import java.time.format.DateTimeFormatter
import scala.concurrent.duration.DurationInt

object LogSockets:
  private val log = AppLogger(getClass)
  private val dateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME

class LogSockets(
  logs: Topic[IO, LogEntryInputs],
  admins: Topic[IO, LogSources],
  connectedSources: Ref[IO, LogSources],
  logUpdates: Topic[IO, AppLogEvents],
  db: LogsDatabase[IO]
):
  private val savedEvents: Stream[IO, AppLogEvents] = logs.subscribe(100).evalMap { ins =>
    db.insert(ins.events).map { written =>
      AppLogEvents(written.rows.map(_.toEvent))
    }
  }
  // saves to the database and publishes events
  val publisher = savedEvents.evalMap { saved =>
    logUpdates.publish1(saved)
  }
  private val logIncoming: Pipe[IO, WebSocketFrame, Unit] = _.evalMap {
    case Text(message, _) => IO(log.info(message))
    case f                => IO(log.debug(s"Unknown WebSocket frame: $f"))
  }
  private val pings = Stream.awakeEvery[IO](5.seconds).map(_ => SimpleEvent.ping)

  def listener(query: StreamsQuery, socketBuilder: WebSocketBuilder2[IO]): IO[Response[IO]] =
    val subscription =
      logUpdates.subscribe(100).map { es =>
        es.filter(_.event.level.int >= query.level.int)
      }
    val filteredEvents =
      if query.apps.isEmpty then subscription
      else
        subscription.map { es =>
          es.filter(e => query.apps.exists(app => app.name == e.source.name.name))
        }
    val logEvents = Stream
      .eval(db.events(query))
      .flatMap { history =>
        Stream(history.reverse) ++ filteredEvents.map(
          _.filter(e => !history.events.exists(_.id == e.id))
        )
      }
      .filter(_.events.nonEmpty)
    val toClient = pings
      .mergeHaltBoth(logEvents)
      .through(jsonTransform[FrontEvent])
    socketBuilder.build(toClient, logIncoming)

  def admin(user: UserRequest, socketBuilder: WebSocketBuilder2[IO]): IO[Response[IO]] =
    socketBuilder
      .withOnClose(IO(log.info(s"Admin '${user.user}' disconnected.")))
      .build(
        Stream.eval(IO(log.info(s"Admin '${user.user}' connected."))) >> pings
          .mergeHaltBoth(Stream.eval(connectedSources.get) ++ admins.subscribe(100))
          .through(jsonTransform[AdminEvent]),
        logIncoming
      )

  def source(user: UserRequest, socketBuilder: WebSocketBuilder2[IO]): IO[Response[IO]] =
    val publishEvents: Pipe[IO, WebSocketFrame, Unit] = _.evalMap {
      case Text(message, _) =>
        log.debug(s"Received $message")
        val event = decode[LogEvents](message).fold(
          err => IO.raiseError(JsonException(err, message)),
          es =>
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
        )
        event.flatMap { e =>
          publishLogged(e, logs)
        }
      case f => IO(log.debug(s"Unknown WebSocket frame: $f"))
    }
    log.info(s"Server ${user.user} with agent ${user.userAgent.getOrElse("unknown")} joined.")
    val id = com.malliina.web.Utils.randomString().take(7)
    val now = user.now
    val date = LogSockets.dateTimeFormatter.format(now)
    val logSource =
      LogSource(
        AppName(user.user.name),
        user.address,
        user.userAgent,
        id,
        now.toInstant.toEpochMilli,
        date
      )
    socketBuilder
      .withOnClose(disconnected(logSource))
      .build(
        Stream.eval(connected(logSource)) >> pings.through(jsonTransform[SimpleEvent]),
        publishEvents
      )

  def connected(src: LogSource): IO[Unit] =
    connectedSources.updateAndGet(olds => LogSources(olds.sources :+ src)).flatMap { connecteds =>
      publishLogged(connecteds, admins)
    }

  def disconnected(src: LogSource): IO[Unit] =
    connectedSources
      .updateAndGet(olds => LogSources(olds.sources.filterNot(_.id == src.id)))
      .flatMap { connecteds =>
        publishLogged(connecteds, admins)
      }

  private def jsonTransform[T: Encoder](src: Stream[IO, T]): Stream[IO, Text] = src.map { t =>
    Text(t.asJson.noSpaces)
  }

  private def publishLogged[T](t: T, to: Topic[IO, T]): IO[Unit] = to.publish1(t).map { res =>
    res.fold(
      closed => log.warn(s"Published $t to closed topic."),
      _ => log.debug(s"Published $t to topic.")
    )
  }
