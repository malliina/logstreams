package com.malliina.logstreams.models

import play.api.libs.json._

case class AppName(name: String) {
  override def toString: String = name
}

object AppName extends Companion[String, AppName] {
  override def raw(t: AppName): String = t.name
}

case class LogEntryId(id: Long) {
  override def toString: String = s"$id"
}

object LogEntryId extends Companion[Long, LogEntryId] {
  override def raw(t: LogEntryId): Long = t.id
}

case class LogSource(name: AppName, remoteAddress: String)

object LogSource {
  implicit val app = AppName.format
  implicit val json = Json.format[LogSource]
}

sealed trait GenericEvent

case class SimpleEvent(event: String) extends FrontEvent with AdminEvent

object SimpleEvent {
  implicit val json = Json.format[SimpleEvent]
  val ping = SimpleEvent("ping")
}

case class LogSources(sources: Seq[LogSource]) extends AdminEvent

object LogSources {
  implicit val json = Json.format[LogSources]
}

sealed trait AdminEvent extends GenericEvent

object AdminEvent {
  implicit val reader = Reads[AdminEvent] { json =>
    LogSources.json.reads(json).orElse(SimpleEvent.json.reads(json))
  }
  implicit val writer = Writes[AdminEvent] {
    case ls@LogSources(_) => LogSources.json.writes(ls)
    case se@SimpleEvent(_) => SimpleEvent.json.writes(se)
  }
}

case class LogEventOld(timeStamp: Long,
                       timeFormatted: String,
                       message: String,
                       loggerName: String,
                       threadName: String,
                       level: String,
                       stackTrace: Option[String] = None) {
  def toEvent = LogEvent(timeStamp, timeFormatted, message, loggerName, threadName, level, stackTrace)
}

object LogEventOld {
  implicit val json = Json.format[LogEventOld]
}

case class LogEvent(timestamp: Long,
                    timeFormatted: String,
                    message: String,
                    loggerName: String,
                    threadName: String,
                    level: String,
                    stackTrace: Option[String] = None)

object LogEvent {
  val reader = Json.reads[LogEvent].orElse(LogEventOld.json.map(_.toEvent))
  implicit val json: OFormat[LogEvent] = OFormat(reader, Json.writes[LogEvent])
}

case class AppLogEvent(id: LogEntryId, source: LogSource, event: LogEvent, added: Long, addedFormatted: String)

object AppLogEvent {
  implicit val json = Json.format[AppLogEvent]
}

case class AppLogEvents(events: Seq[AppLogEvent]) extends FrontEvent {
  def filter(p: AppLogEvent => Boolean): AppLogEvents = copy(events = events.filter(p))
  def reverse = AppLogEvents(events.reverse)
}

object AppLogEvents {
  implicit val json = Json.format[AppLogEvents]
}

sealed trait FrontEvent extends GenericEvent

object FrontEvent {
  implicit val reader = Reads[FrontEvent] { json =>
    AppLogEvents.json.reads(json).orElse(SimpleEvent.json.reads(json))
  }
  implicit val writer = Writes[FrontEvent] {
    case ale@AppLogEvents(_) => AppLogEvents.json.writes(ale)
    case se@SimpleEvent(_) => SimpleEvent.json.writes(se)
  }
}

abstract class Companion[Raw, T](implicit jsonFormat: Format[Raw], o: Ordering[Raw]) {
  def apply(raw: Raw): T

  def raw(t: T): Raw

  implicit val format: Format[T] = Format(
    Reads[T](in => in.validate[Raw].map(apply)),
    Writes[T](t => Json.toJson(raw(t)))
  )

  implicit val ordering: Ordering[T] = o.on(raw)
}
