package com.malliina.logstreams

import com.malliina.logstreams.models.LogSource

sealed trait SourceMessage

object SourceMessage {
  case class SourceJoined(source: LogSource) extends SourceMessage
  case class SourceLeft(source: LogSource) extends SourceMessage
  case object Ping extends SourceMessage
}
