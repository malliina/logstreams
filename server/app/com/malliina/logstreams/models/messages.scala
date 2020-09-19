package com.malliina.logstreams.models

import java.time.{Instant, ZoneId}
import java.util.TimeZone

import ch.qos.logback.classic.Level
import com.malliina.logback.LogbackFormatting
import com.malliina.values.Username
import play.api.libs.json.{Json, OFormat}

case class LogEvents(events: List[LogEvent])

object LogEvents {
  implicit val json: OFormat[LogEvents] = Json.format[LogEvents]
}

case class LogEntryInput(
  appName: Username,
  remoteAddress: String,
  timestamp: Instant,
  message: String,
  loggerName: String,
  threadName: String,
  level: Level,
  stackTrace: Option[String]
)

case class LogEntryInputs(events: List[LogEntryInput])

case class LogEntryRow(
  id: LogEntryId,
  app: Username,
  address: String,
  timestamp: Instant,
  message: String,
  logger: String,
  thread: String,
  level: Level,
  stacktrace: Option[String],
  added: Instant
) {
  def toEvent = AppLogEvent(
    id,
    LogSource(AppName(app.name), address),
    LogEvent(
      timestamp.toEpochMilli,
      LogEntryRow.format(timestamp),
      message,
      logger,
      thread,
      level.levelStr,
      stacktrace
    ),
    added.toEpochMilli,
    LogEntryRow.format(added)
  )
}

object LogEntryRow {
  LogbackFormatting.defaultFormatter.formatter.setTimeZone(
    TimeZone.getTimeZone(ZoneId.of("Europe/Helsinki"))
  )
  def format(i: Instant) = LogbackFormatting.defaultFormatter.format(i.toEpochMilli)
}

case class EntriesWritten(inputs: Seq[LogEntryInput], rows: Seq[LogEntryRow]) {
  def isCountOk = inputs.length == rows.length
}
