package com.malliina.logstreams

import com.malliina.logstreams.models.LogSource

enum SourceMessage:
  case SourceJoined(source: LogSource) extends SourceMessage
  case SourceLeft(source: LogSource) extends SourceMessage
  case Ping extends SourceMessage
