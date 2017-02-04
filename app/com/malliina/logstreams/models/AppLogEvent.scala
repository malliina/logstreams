package com.malliina.logstreams.models

import com.malliina.logbackrx.LogEvent
import com.malliina.play.json.SimpleCompanion
import play.api.data.format.Formats.stringFormat
import play.api.libs.json.Json

case class AppName(name: String)

object AppName extends SimpleCompanion[String, AppName] {
  override def raw(t: AppName) = t.name
}

case class LogSource(name: AppName, remoteAddress: String)

object LogSource {
  implicit val json = Json.format[LogSource]
}

case class AppLogEvent(source: LogSource, event: LogEvent)

object AppLogEvent {
  implicit val json = Json.format[AppLogEvent]
}
