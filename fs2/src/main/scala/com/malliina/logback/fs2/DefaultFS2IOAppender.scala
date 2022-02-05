package com.malliina.logback.fs2

import cats.effect.{IO, Resource}
import cats.effect.std.Dispatcher
import ch.qos.logback.classic.spi.ILoggingEvent
import com.malliina.logback.{LogEvent, TimeFormatting}
import fs2.Stream
import fs2.concurrent.{SignallingRef, Topic}

class DefaultFS2IOAppender(comps: LoggingComps)
  extends FS2IOAppender[ILoggingEvent](comps)
  with TimeFormatting[ILoggingEvent]:
  val logEvents: Stream[IO, LogEvent] =
    source.map(e => LogEvent.fromLogbackEvent(e, format))
