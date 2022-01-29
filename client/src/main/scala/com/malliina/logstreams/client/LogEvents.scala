package com.malliina.logstreams.client

import com.malliina.logback.LogEvent
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class LogEvents(events: Seq[LogEvent])

object LogEvents:
  implicit val json: Codec[LogEvents] = deriveCodec[LogEvents]
