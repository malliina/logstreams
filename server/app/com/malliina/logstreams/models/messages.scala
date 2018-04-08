package com.malliina.logstreams.models

import java.time.Instant

import ch.qos.logback.classic.Level
import com.malliina.logbackrx.LogEvent
import com.malliina.play.json.SimpleCompanion
import com.malliina.play.models.Username
import play.api.data.format.Formats.{longFormat, stringFormat}
import play.api.libs.json.Json

case class LogEvents(events: Seq[LogEvent])

object LogEvents {
  implicit val json = Json.format[LogEvents]
}

case class AppName(name: String) {
  override def toString = name
}

object AppName extends SimpleCompanion[String, AppName] {
  override def raw(t: AppName) = t.name
}

case class LogSource(name: AppName, remoteAddress: String)

object LogSource {
  implicit val json = Json.format[LogSource]
}

case class LogSources(sources: Seq[LogSource])

object LogSources {
  implicit val json = Json.format[LogSources]
  val empty = LogSources(Nil)
}

case class AppLogEvent(source: LogSource, event: LogEvent) {
  def toInput = LogEntryInput(
    Username(source.name.name),
    source.remoteAddress,
    Instant.ofEpochMilli(event.timeStamp),
    event.message,
    event.loggerName,
    event.threadName,
    event.level,
    event.stackTrace
  )
}

object AppLogEvent {
  implicit val json = Json.format[AppLogEvent]
}

case class AppLogEvents(events: Seq[AppLogEvent])

object AppLogEvents {
  implicit val json = Json.format[AppLogEvents]
}

case class LogEntryId(id: Long)

object LogEntryId extends SimpleCompanion[Long, LogEntryId] {
  override def raw(t: LogEntryId): Long = t.id
}

case class LogEntryInput(appName: Username,
                         remoteAddress: String,
                         timestamp: Instant,
                         message: String,
                         loggerName: String,
                         threadName: String,
                         level: Level,
                         stackTrace: Option[String])

case class LogEntryRow(id: LogEntryId,
                       appName: Username,
                       remoteAddress: String,
                       timestamp: Instant,
                       message: String,
                       loggerName: String,
                       threadName: String,
                       level: Level,
                       stackTrace: Option[String],
                       added: Instant)
