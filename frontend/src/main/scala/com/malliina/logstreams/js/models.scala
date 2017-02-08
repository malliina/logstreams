package com.malliina.logstreams.js

case class LogSources(sources: Seq[LogSource])

case class LogSource(name: String, remoteAddress: String)

case class LogEvent(timeStamp: Long,
                    timeFormatted: String,
                    message: String,
                    loggerName: String,
                    threadName: String,
                    level: String,
                    stackTrace: Option[String] = None)

case class AppLogEvent(source: LogSource, event: LogEvent)

case class AppLogEvents(events: Seq[AppLogEvent])
