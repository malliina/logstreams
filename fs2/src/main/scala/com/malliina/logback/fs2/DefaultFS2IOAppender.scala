package com.malliina.logback.fs2

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import ch.qos.logback.classic.spi.ILoggingEvent
import com.malliina.logback.{LogEvent, TimeFormatting}
import fs2.Stream

import scala.concurrent.ExecutionContext

object DefaultFS2IOAppender {
  def apply(rt: IORuntime): DefaultFS2IOAppender = new DefaultFS2IOAppender(rt)
}

class DefaultFS2IOAppender(rt: IORuntime)
  extends FS2IOAppender[ILoggingEvent]()(rt)
  with TimeFormatting[ILoggingEvent] {
  val logEvents: Stream[IO, LogEvent] =
    source.map(e => LogEvent.fromLogbackEvent(e, format))
}
