package com.malliina.logstreams.js

import play.api.libs.json.Json

case class LogSource(name: String, remoteAddress: String)

object LogSource {
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

case class AppLogEvent(source: LogSource, event: LogEvent)

object AppLogEvent {
  implicit val json = Json.format[AppLogEvent]
}

case class AppLogEvents(events: Seq[AppLogEvent])

object AppLogEvents {
  implicit val json = Json.format[AppLogEvents]
}
