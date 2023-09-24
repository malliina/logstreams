package com.malliina.logstreams.models

import com.malliina.logstreams.models
import com.malliina.logstreams.models.LogsJson.evented
import com.malliina.values.{EnumCompanion, ErrorMessage, WrappedString}
import io.circe.*
import io.circe.generic.semiauto.*
import io.circe.syntax.*
import cats.syntax.functor.*

sealed abstract class LogLevel(val name: String, val int: Int) extends WrappedString:
  override def value = name

object LogLevel extends EnumCompanion[String, LogLevel]:
  val Key = "level"
  override val all: Seq[LogLevel] = Seq(Trace, Debug, Info, Warn, Error, Other)
  override def write(t: LogLevel): String = t.name

  case object Trace extends LogLevel("trace", 10)
  case object Debug extends LogLevel("debug", 20)
  case object Info extends LogLevel("info", 30)
  case object Warn extends LogLevel("warn", 40)
  case object Error extends LogLevel("error", 50)
  case object Other extends LogLevel("other", 60)

  override def build(input: String): Either[ErrorMessage, LogLevel] =
    all
      .find(i => write(i).toLowerCase == input.toLowerCase)
      .toRight(defaultError(input))

  def of(i: Int): Option[LogLevel] = all.find(_.int == i)
  def unsafe(i: Int) = of(i).getOrElse(
    throw IllegalArgumentException(
      s"Invalid log level: '$i'. Must be one of ${all.map(_.int).mkString(", ")}"
    )
  )

  override implicit val ordering: Ordering[LogLevel] = Ordering.by[LogLevel, Int](_.int)

case class AppName(name: String) extends AnyVal:
  override def toString: String = name

object AppName extends Companion[String, AppName]:
  override def raw(t: AppName): String = t.name

case class LogEntryId(id: Long) extends AnyVal:
  override def toString: String = s"$id"

object LogEntryId extends Companion[Long, LogEntryId]:
  override def raw(t: LogEntryId): Long = t.id

case class SimpleLogSource(name: AppName, remoteAddress: String) derives Codec.AsObject

case class LogSource(
  name: AppName,
  remoteAddress: String,
  userAgent: Option[String],
  id: String,
  joined: Long,
  joinedFormatted: String,
  timeJoined: String
) derives Codec.AsObject

sealed trait GenericEvent
sealed trait FrontEvent extends GenericEvent

case class SimpleEvent(event: String) extends FrontEvent with AdminEvent derives Codec.AsObject

object SimpleEvent:
  val ping = SimpleEvent("ping")

case class LogSources(sources: Seq[LogSource]) extends AdminEvent derives Codec.AsObject

sealed trait AdminEvent extends GenericEvent

object AdminEvent:
  implicit val decoder: Decoder[AdminEvent] =
    Decoder[LogSources].or(Decoder[SimpleEvent].map[AdminEvent](identity))
  implicit val encoder: Encoder[AdminEvent] = {
    case ls @ LogSources(_)  => ls.asJson
    case se @ SimpleEvent(_) => se.asJson
  }

case class LogEventOld(
  timeStamp: Long,
  timeFormatted: String,
  message: String,
  loggerName: String,
  threadName: String,
  level: String,
  stackTrace: Option[String] = None
) derives Codec.AsObject:
  def toEvent =
    LogEvent(
      timeStamp,
      timeFormatted,
      message,
      loggerName,
      threadName,
      LogLevel.build(level).getOrElse(LogLevel.Info),
      stackTrace
    )

case class LogEvent(
  timestamp: Long,
  timeFormatted: String,
  message: String,
  loggerName: String,
  threadName: String,
  level: LogLevel,
  stackTrace: Option[String] = None
)

object LogEvent:
  private val basicReader: Decoder[LogEvent] = deriveDecoder[LogEvent]
  private val reader = basicReader.or(Decoder[LogEventOld].map(_.toEvent))
  implicit val json: Codec[LogEvent] = Codec.from(reader, deriveEncoder[LogEvent])

case class AppLogEvent(
  id: LogEntryId,
  source: SimpleLogSource,
  event: LogEvent,
  added: Long,
  addedFormatted: String
) derives Codec.AsObject

case class AppLogEvents(events: Seq[AppLogEvent]) extends FrontEvent:
  def filter(p: AppLogEvent => Boolean): AppLogEvents = copy(events = events.filter(p))
  def reverse = AppLogEvents(events.reverse)
  def isEmpty = events.isEmpty

object AppLogEvents:
  implicit val json: Codec[AppLogEvents] = evented("events", deriveCodec[AppLogEvents])

case class SearchInfo(query: Option[String], from: Option[String], to: Option[String])
  derives Codec.AsObject

case class MetaEvent(event: String, meta: SearchInfo) extends FrontEvent derives Codec.AsObject
object MetaEvent:
  val Loading = "loading"
  val NoData = "noData"
  def noData(meta: SearchInfo) = MetaEvent(NoData, meta)
  def loading(meta: SearchInfo) = MetaEvent(Loading, meta)

object FrontEvent:
  implicit val reader: Decoder[FrontEvent] =
    Decoder[AppLogEvents]
      .or(Decoder[MetaEvent].map[FrontEvent](identity))
      .or(Decoder[SimpleEvent].map[FrontEvent](identity))
  implicit val encoder: Encoder[FrontEvent] = {
    case ale @ AppLogEvents(_) => ale.asJson
    case me @ MetaEvent(_, _)  => me.asJson
    case se @ SimpleEvent(_)   => se.asJson
  }

abstract class Companion[Raw, T](implicit d: Decoder[Raw], e: Encoder[Raw], o: Ordering[Raw]):
  def apply(raw: Raw): T
  def raw(t: T): Raw

  given Codec[T] = Codec.from(
    d.map(in => apply(in)),
    e.contramap[T](t => raw(t))
  )

  given Ordering[T] = o.on(raw)

object LogsJson:
  private val EventKey = "event"
  private val eventDecoder = Decoder.decodeString.at(EventKey)

  def evented[T](value: String, codec: Codec.AsObject[T]): Codec.AsObject[T] =
    val decoder = eventDecoder.flatMap[T] { event =>
      if event == value then codec
      else Decoder.failed(DecodingFailure(s"Event is '$event', required '$value'.", Nil))
    }
    val encoder: Encoder.AsObject[T] = (t: T) =>
      codec.encodeObject(t).deepMerge(JsonObject(EventKey -> value.asJson))
    Codec.AsObject.from(decoder, encoder)
