package com.malliina.logstreams.client

import com.malliina.logbackrx.LogEvent
import play.api.libs.json.Json

case class LogEvents(events: Seq[LogEvent])

object LogEvents {
  implicit val json = Json.format[LogEvents]
}
