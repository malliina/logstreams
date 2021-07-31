package com.malliina.logstreams.models

import com.malliina.values.{EnumCompanion, WrappedString}
import io.circe._
import io.circe.generic.semiauto._

sealed abstract class LogLevel(val name: String, val int: Int) extends WrappedString {
  override def value = name
}

object LogLevel extends EnumCompanion[String, LogLevel] {
  val Key = "level"
  override val all: Seq[LogLevel] = Seq(Trace, Debug, Info, Warn, Error, Other)
  override def write(t: LogLevel): String = t.name

  case object Trace extends LogLevel("trace", 10)
  case object Debug extends LogLevel("debug", 20)
  case object Info extends LogLevel("info", 30)
  case object Warn extends LogLevel("warn", 40)
  case object Error extends LogLevel("error", 50)
  case object Other extends LogLevel("other", 60)

  def of(i: Int): Option[LogLevel] = all.find(_.int == i)

  override implicit val ordering: Ordering[LogLevel] = Ordering.by[LogLevel, Int](_.int)
}

case class AppName(name: String) extends AnyVal {
  override def toString: String = name
}

object AppName extends Companion[String, AppName] {
  override def raw(t: AppName): String = t.name
}

case class LogEntryId(id: Long) extends AnyVal {
  override def toString: String = s"$id"
}

object LogEntryId extends Companion[Long, LogEntryId] {
  override def raw(t: LogEntryId): Long = t.id
}

case class LogSource(name: AppName, remoteAddress: String)

object LogSource {
  implicit val json: Codec[LogSource] = deriveCodec[LogSource]
}

sealed trait GenericEvent

case class SimpleEvent(event: String) extends FrontEvent with AdminEvent

object SimpleEvent {
  implicit val json: Codec[SimpleEvent] = deriveCodec[SimpleEvent]
  val ping = SimpleEvent("ping")
}

case class LogSources(sources: Seq[LogSource]) extends AdminEvent

object LogSources {
  implicit val json: Codec[LogSources] = deriveCodec[LogSources]
}

sealed trait AdminEvent extends GenericEvent

object AdminEvent {
  implicit val reader = Reads[AdminEvent] { json =>
    LogSources.json.reads(json).orElse(SimpleEvent.json.reads(json))
  }
  implicit val writer = Writes[AdminEvent] {
    case ls @ LogSources(_)  => LogSources.json.writes(ls)
    case se @ SimpleEvent(_) => SimpleEvent.json.writes(se)
  }
}

case class LogEventOld(
  timeStamp: Long,
  timeFormatted: String,
  message: String,
  loggerName: String,
  threadName: String,
  level: String,
  stackTrace: Option[String] = None
) {
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
}

object LogEventOld {
  implicit val json: Codec[LogEventOld] = deriveCodec[LogEventOld]
}

case class LogEvent(
  timestamp: Long,
  timeFormatted: String,
  message: String,
  loggerName: String,
  threadName: String,
  level: LogLevel,
  stackTrace: Option[String] = None
)

object LogEvent {
  val reader = Json.reads[LogEvent].orElse(LogEventOld.json.map(_.toEvent))
  implicit val json: OFormat[LogEvent] = OFormat(reader, Json.writes[LogEvent])
}

case class AppLogEvent(
  id: LogEntryId,
  source: LogSource,
  event: LogEvent,
  added: Long,
  addedFormatted: String
)

object AppLogEvent {
  implicit val json: Codec[AppLogEvent] = deriveCodec[AppLogEvent]
}

case class AppLogEvents(events: Seq[AppLogEvent]) extends FrontEvent {
  def filter(p: AppLogEvent => Boolean): AppLogEvents = copy(events = events.filter(p))
  def reverse = AppLogEvents(events.reverse)
}

object AppLogEvents {
  implicit val json: Codec[AppLogEvents] = deriveCodec[AppLogEvents]
}

sealed trait FrontEvent extends GenericEvent

object FrontEvent {
  implicit val reader = Reads[FrontEvent] { json =>
    AppLogEvents.json.reads(json).orElse(SimpleEvent.json.reads(json))
  }
  implicit val writer = Writes[FrontEvent] {
    case ale @ AppLogEvents(_) => AppLogEvents.json.writes(ale)
    case se @ SimpleEvent(_)   => SimpleEvent.json.writes(se)
  }
}

abstract class Companion[Raw, T](implicit d: Decoder[Raw], e: Encoder[Raw], o: Ordering[Raw]) {
  def apply(raw: Raw): T
  def raw(t: T): Raw

  implicit val format: Codec[T] = Codec.from(
    ???,
    ???
//    Reads[T](in => in.validate[Raw].map(apply)),
//    Writes[T](t => Json.toJson(raw(t)))
  )

  implicit val ordering: Ordering[T] = o.on(raw)
}
