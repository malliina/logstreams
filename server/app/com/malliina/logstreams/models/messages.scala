package com.malliina.logstreams.models

import java.time.Instant

import ch.qos.logback.classic.Level
import com.malliina.logbackrx.RxLogback
import com.malliina.values.Username
import play.api.libs.json.Json

case class LogEvents(events: Seq[LogEvent])

object LogEvents {
  implicit val json = Json.format[LogEvents]
}

case class LogEntryInput(appName: Username,
                         remoteAddress: String,
                         timestamp: Instant,
                         message: String,
                         loggerName: String,
                         threadName: String,
                         level: Level,
                         stackTrace: Option[String])

case class LogEntryInputs(events: Seq[LogEntryInput])

case class LogEntryRow(id: LogEntryId,
                       appName: Username,
                       remoteAddress: String,
                       timestamp: Instant,
                       message: String,
                       loggerName: String,
                       threadName: String,
                       level: Level,
                       stackTrace: Option[String],
                       added: Instant) {
  def toEvent = AppLogEvent(
    id,
    LogSource(AppName(appName.name), remoteAddress),
    LogEvent(timestamp.toEpochMilli, LogEntryRow.format(timestamp),
      message, loggerName, threadName, level.levelStr, stackTrace),
    added.toEpochMilli, LogEntryRow.format(added)
  )
}

object LogEntryRow {
  def format(date: Instant) = RxLogback.defaultFormatter.format(date.toEpochMilli)
}

case class EntriesWritten(inputs: Seq[LogEntryInput], rows: Seq[LogEntryRow]) {
  def isCountOk = inputs.length == rows.length
}
