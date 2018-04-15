package com.malliina.logstreams.models

import play.api.libs.json.{Format, Json, Reads, Writes}

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

case class LogSources(sources: Seq[LogSource])

object LogSources {
  implicit val json = Json.format[LogSources]
}

case class LogEvent(timeStamp: Long,
                    timeFormatted: String,
                    message: String,
                    loggerName: String,
                    threadName: String,
                    level: String,
                    stackTrace: Option[String] = None)

object LogEvent {
  implicit val json = Json.format[LogEvent]
}

case class AppLogEvent(id: LogEntryId, source: LogSource, event: LogEvent, added: Long, addedFormatted: String)

object AppLogEvent {
  implicit val json = Json.format[AppLogEvent]
}

case class AppLogEvents(events: Seq[AppLogEvent])

object AppLogEvents {
  implicit val json = Json.format[AppLogEvents]
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
