package com.malliina.logstreams.client

import com.malliina.logback.LogEvent
import io.circe.Codec

case class LogEvents(events: Seq[LogEvent]) derives Codec.AsObject
