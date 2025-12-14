package com.malliina.logstreams.http4s

import cats.effect.kernel.{Async, Ref}
import cats.syntax.all.{toFlatMapOps, toFunctorOps}
import com.malliina.logback.{LogbackFormatting, TimeFormatter}
import com.malliina.logstreams.db.{LogsDatabase, StreamsQuery}
import com.malliina.logstreams.http4s.LogSockets.{instantFormatter, log}
import com.malliina.logstreams.models.*
import com.malliina.util.AppLogger
import fs2.concurrent.Topic
import fs2.{Pipe, Stream}
import io.circe.Encoder
import io.circe.parser.decode
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
  private val instantFormatter = DateTimeFormatter.ISO_INSTANT

class LogSockets[F[_]: Async](
  logs: Topic[F, LogEntryInputs],
  admins: Topic[F, LogSources],
  connectedSources: Ref[F, LogSources],
  logUpdates: Topic[F, AppLogEvents],
  val db: LogsDatabase[F]
):
  val F = Async[F]
  private val formatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(TimeFormatter.helsinki)
  private val savedEvents: Stream[F, AppLogEvents] = logs
    .subscribe(100)
    .evalMap: ins =>
      db.insert(ins.events)
        .map: written =>
          AppLogEvents(written.rows.map(_.toEvent))
  // saves to the database and publishes events
  val publisher = savedEvents.evalMap: saved =>
    logUpdates.publish1(saved)
  private val logIncoming: Pipe[F, WebSocketFrame, Unit] = _.evalMap:
    case Text(message, _) => F.delay(log.info(message))
    case f                => F.delay(log.debug(s"Unknown WebSocket frame: $f"))
  private val pings = Stream.awakeEvery[F](5.seconds).map(_ => SimpleEvent.ping)

  def listener(query: StreamsQuery, socketBuilder: WebSocketBuilder2[F]): F[Response[F]] =
    val subscription = logUpdates
      .subscribe(100)
      .map: es =>
        es.filter(_.event.level.int >= query.level.int)
    val filteredEvents =
      if query.offset.value > 0 then Stream.empty
      else if query.query.isDefined then Stream.empty
      else if query.apps.isEmpty then subscription
      else
        subscription.map: es =>
          es.filter(e => query.apps.exists(app => app.name == e.source.name.name))

    val info = query.toJs(formatter)
    val logEvents = Stream(MetaEvent.loading(info)) ++ Stream
      .eval(db.events(query))
      .flatMap: history =>
        val historyOrNoData =
          if history.isEmpty then MetaEvent.noData(info)
          else history.reverse
        Stream(historyOrNoData) ++ filteredEvents
          .map(
            _.filter(e => !history.events.exists(_.id == e.id))
          )
          .filter(es => !es.isEmpty)
    val toClient = pings
      .mergeHaltBoth(logEvents)
      .through(jsonTransform[FrontEvent])
    socketBuilder.build(toClient, logIncoming)

  def admin(user: UserRequest, socketBuilder: WebSocketBuilder2[F]): F[Response[F]] =
    socketBuilder
      .withOnClose(F.delay(log.info(s"Admin '${user.user}' disconnected.")))
      .build(
        Stream.eval(F.delay(log.info(s"Admin '${user.user}' connected."))) >> pings
          .mergeHaltBoth(Stream.eval(connectedSources.get) ++ admins.subscribe(100))
          .through(jsonTransform[AdminEvent]),
        logIncoming
      )

  def source(user: UserRequest, socketBuilder: WebSocketBuilder2[F]): F[Response[F]] =
    val publishEvents: Pipe[F, WebSocketFrame, Unit] = _.evalMap:
      case Text(message, _) =>
        log.debug(s"Received '$message' from ${user.user}.")
        val event = decode[LogEvents](message).fold(
          err =>
            log.warn(s"Failed to decode '$message' from ${user.user}.")
            F.raiseError(JsonException(err, message))
          ,
          es =>
            val events = es.events.map: event =>
              LogEntryInput(
                user.user,
                user.address,
                Instant.ofEpochMilli(event.timestamp),
                event.message,
                event.loggerName,
                event.threadName,
                event.level,
                user.clientId,
                user.userAgent,
                event.stackTrace
              )
            F.pure(LogEntryInputs(events))
        )
        event.flatMap: e =>
          publishLogged(e, logs)
      case f => F.delay(log.debug(s"Unknown WebSocket frame: $f"))
    log.info(s"Server ${user.user} with agent ${user.userAgent.getOrElse("unknown")} joined.")
    val id = com.malliina.web.Utils.randomString().take(7)
    val now = user.now
    val date = LogSockets.dateTimeFormatter.format(now)
    val logSource =
      LogSource(
        AppName.fromUsername(user.user),
        user.address,
        user.userAgent,
        id,
        now.toInstant.toEpochMilli,
        date,
        LogEntryRow.format(now.toInstant)
      )
    socketBuilder
      .withOnClose(disconnected(logSource))
      .build(
        Stream.eval(connected(logSource)) >> pings.through(jsonTransform[SimpleEvent]),
        publishEvents
      )

  private def connected(src: LogSource): F[Unit] =
    connectedSources
      .updateAndGet(olds => LogSources(olds.sources :+ src))
      .flatMap: connecteds =>
        publishLogged(connecteds, admins)

  def disconnected(src: LogSource): F[Unit] =
    connectedSources
      .updateAndGet(olds => LogSources(olds.sources.filterNot(_.id == src.id)))
      .flatMap: connecteds =>
        log.info(s"Disconnection, now connected $connecteds")
        publishLogged(connecteds, admins)

  private def jsonTransform[T: Encoder](src: Stream[F, T]): Stream[F, Text] = src.map: t =>
    Text(t.asJson.noSpaces)

  private def publishLogged[T](t: T, to: Topic[F, T]): F[Unit] = to
    .publish1(t)
    .map: res =>
      res.fold(
        closed => log.warn(s"Published $t to closed topic."),
        _ => log.debug(s"Published $t to topic.")
      )
