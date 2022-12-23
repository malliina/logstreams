package com.malliina.logstreams.models

import com.malliina.values.{EnumCompanion, ErrorMessage, WrappedString}
import io.circe.*
import io.circe.generic.semiauto.*
import io.circe.syntax.*

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

  override implicit val ordering: Ordering[LogLevel] = Ordering.by[LogLevel, Int](_.int)

case class AppName(name: String) extends AnyVal:
  override def toString: String = name

object AppName extends Companion[String, AppName]:
  override def raw(t: AppName): String = t.name

case class LogEntryId(id: Long) extends AnyVal:
  override def toString: String = s"$id"

object LogEntryId extends Companion[Long, LogEntryId]:
  override def raw(t: LogEntryId): Long = t.id

case class SimpleLogSource(name: AppName, remoteAddress: String)

object SimpleLogSource:
  implicit val json: Codec[SimpleLogSource] = deriveCodec[SimpleLogSource]

case class LogSource(
  name: AppName,
  remoteAddress: String,
  userAgent: Option[String],
  id: String,
  joined: Long,
  joinedFormatted: String
)

object LogSource:
  implicit val json: Codec[LogSource] = deriveCodec[LogSource]

sealed trait GenericEvent

case class SimpleEvent(event: String) extends FrontEvent with AdminEvent

object SimpleEvent:
  implicit val json: Codec[SimpleEvent] = deriveCodec[SimpleEvent]
  val ping = SimpleEvent("ping")

case class LogSources(sources: Seq[LogSource]) extends AdminEvent

object LogSources:
  implicit val json: Codec[LogSources] = deriveCodec[LogSources]

sealed trait AdminEvent extends GenericEvent

object AdminEvent:
  implicit val decoder: Decoder[AdminEvent] =
    LogSources.json.or(SimpleEvent.json.map[AdminEvent](identity))
  implicit val encoder: Encoder[AdminEvent] = new Encoder[AdminEvent]:
    final def apply(a: AdminEvent): Json = a match
      case ls @ LogSources(_)  => ls.asJson
      case se @ SimpleEvent(_) => se.asJson

case class LogEventOld(
  timeStamp: Long,
  timeFormatted: String,
  message: String,
  loggerName: String,
  threadName: String,
  level: String,
  stackTrace: Option[String] = None
):
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

object LogEventOld:
  implicit val json: Codec[LogEventOld] = deriveCodec[LogEventOld]

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
  val basicReader: Decoder[LogEvent] = deriveDecoder[LogEvent]
  val reader = basicReader.or(LogEventOld.json.map(_.toEvent))
  implicit val json: Codec[LogEvent] = Codec.from(reader, deriveEncoder[LogEvent])

case class AppLogEvent(
  id: LogEntryId,
  source: SimpleLogSource,
  event: LogEvent,
  added: Long,
  addedFormatted: String
)

object AppLogEvent:
  implicit val json: Codec[AppLogEvent] = deriveCodec[AppLogEvent]

case class AppLogEvents(events: Seq[AppLogEvent]) extends FrontEvent:
  def filter(p: AppLogEvent => Boolean): AppLogEvents = copy(events = events.filter(p))
  def reverse = AppLogEvents(events.reverse)

object AppLogEvents:
  val encoder: Encoder[AppLogEvents] = deriveEncoder[AppLogEvents]
  implicit val json: Codec[AppLogEvents] = deriveCodec[AppLogEvents]

sealed trait FrontEvent extends GenericEvent

object FrontEvent:
  implicit val reader: Decoder[FrontEvent] =
    AppLogEvents.json.or(SimpleEvent.json.map[FrontEvent](identity))
  implicit val encoder: Encoder[FrontEvent] = new Encoder[FrontEvent]:
    final def apply(a: FrontEvent): Json = a match
      case ale @ AppLogEvents(_) => ale.asJson
      case se @ SimpleEvent(_)   => se.asJson

abstract class Companion[Raw, T](implicit d: Decoder[Raw], e: Encoder[Raw], o: Ordering[Raw]):
  def apply(raw: Raw): T
  def raw(t: T): Raw

  implicit val format: Codec[T] = Codec.from(
    d.map(in => apply(in)),
    e.contramap[T](t => raw(t))
  )

  implicit val ordering: Ordering[T] = o.on(raw)
