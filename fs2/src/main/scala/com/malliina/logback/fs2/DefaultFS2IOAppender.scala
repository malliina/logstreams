package com.malliina.logback.fs2

import cats.effect.kernel.Async
import cats.effect.std.Dispatcher
import cats.effect.{IO, Resource}
import ch.qos.logback.classic.spi.ILoggingEvent
import com.malliina.logback.{LogEvent, TimeFormatting}
import fs2.Stream
import fs2.concurrent.{SignallingRef, Topic}

class DefaultFS2IOAppender[F[_]: Async](comps: LoggingComps[F])
  extends FS2IOAppender[F, ILoggingEvent](comps)
  with TimeFormatting[ILoggingEvent]:
  val logEvents: Stream[F, LogEvent] =
    source.map(e => LogEvent.fromLogbackEvent(e, format))
