package com.malliina.logstreams.client

import com.malliina.logbackrx.LogEvent
import play.api.libs.json.{Json, Writes}

case class LogEvents(events: Seq[LogEvent])

object LogEvents {
  implicit val eventWriter = Writes[LogEvent] { e =>
    LogEvent.format.writes(e) ++ Json.obj("timestamp" -> e.timeStamp)
  }
  implicit val json = Json.format[LogEvents]
}
