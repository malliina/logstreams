package com.malliina.logstreams.models

import ch.qos.logback.classic.Level
import com.malliina.logback.LogbackFormatting
import com.malliina.values.Username
import io.circe.{Codec, Decoder, Encoder}

import java.time.Instant
import java.time.format.DateTimeFormatter
import scala.util.Try

case class LogEvents(events: List[LogEvent]) derives Codec.AsObject

case class ParsedLogEvent(
  timestamp: Instant,
  message: String,
  loggerName: String,
  threadName: String,
  level: LogLevel,
  stackTrace: Option[String] = None
) derives Encoder.AsObject

object ParsedLogEvent:
  private val typedJson = Decoder.derived[ParsedLogEvent]
  private val indirectJson = Decoder[LogEvent].emap(e => fromPlain(e))
  given Decoder[ParsedLogEvent] = typedJson.or(indirectJson)

  private def fromPlain(e: LogEvent): Either[String, ParsedLogEvent] =
    val instant = e.isoTimestamp
      .map: str =>
        Try(Instant.parse(str)).toEither.left.map: t =>
          Option(t.getMessage).getOrElse("Invalid timestamp.")
      .getOrElse(e.timestamp.map(ms => Instant.ofEpochMilli(ms)).toRight("No timestamp."))
    instant.map: i =>
      ParsedLogEvent(i, e.message, e.loggerName, e.threadName, e.level, e.stackTrace)

case class ParsedLogEvents(events: List[ParsedLogEvent]) derives Codec.AsObject

case class LogEntryInput(
  appName: Username,
  remoteAddress: String,
  timestamp: Instant,
  message: String,
  loggerName: String,
  threadName: String,
  level: LogLevel,
  clientId: Option[LogClientId],
  userAgent: Option[UserAgent],
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
  level: LogLevel,
  clientId: Option[LogClientId],
  userAgent: Option[UserAgent],
  stacktrace: Option[String],
  added: Instant
):
  def toEvent = AppLogEvent(
    id,
    SimpleLogSource(AppName.fromUsername(app), address, clientId, userAgent),
    LogEvent(
      Option(DateTimeFormatter.ISO_INSTANT.format(timestamp)),
      Option(timestamp.toEpochMilli),
      Option(LogEntryRow.format(timestamp)),
      message,
      logger,
      thread,
      level,
      stacktrace
    ),
    added.toEpochMilli,
    LogEntryRow.format(added)
  )

object LogEntryRow:
  def format(i: Instant) = LogbackFormatting.defaultFormatter.format(i.toEpochMilli)

  def toLevel(l: Level): LogLevel = l.levelInt match
    case Level.TRACE_INT => LogLevel.Trace
    case Level.DEBUG_INT => LogLevel.Debug
    case Level.INFO_INT  => LogLevel.Info
    case Level.WARN_INT  => LogLevel.Warn
    case Level.ERROR_INT => LogLevel.Error
    case _               => LogLevel.Other

case class EntriesWritten(inputs: Seq[LogEntryInput], rows: Seq[LogEntryRow]):
  def isCountOk = inputs.length == rows.length
