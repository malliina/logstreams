package com.malliina.logstreams.client

import com.malliina.logback.LogEvent
import play.api.libs.json.{Json, OFormat}

case class LogEvents(events: Seq[LogEvent])

object LogEvents {
  implicit val json: OFormat[LogEvents] = Json.format[LogEvents]
}
