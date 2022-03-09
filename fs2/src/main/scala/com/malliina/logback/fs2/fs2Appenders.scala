package com.malliina.logback.fs2

import cats.effect.{Concurrent, IO, Resource}
import cats.effect.std.Dispatcher
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import fs2.Stream
import fs2.concurrent.{SignallingRef, Topic}

class FS2IOAppender[E](comps: FS2AppenderComps[E]) extends FS2Appender[E](comps):
  override def append(eventObject: E): Unit =
    d.unsafeRunAndForget(topic.publish1(Option(eventObject)))

type LoggingComps = FS2AppenderComps[ILoggingEvent]

case class FS2AppenderComps[E](
  topic: Topic[IO, Option[E]],
  signal: SignallingRef[IO, Boolean],
  d: Dispatcher[IO]
)

object FS2AppenderComps:
  def resource: Resource[IO, LoggingComps] =
    Dispatcher[IO].evalMap(d => io(d))
  def io(d: Dispatcher[IO]): IO[LoggingComps] = for
    topic <- Topic[IO, Option[ILoggingEvent]]
    signal <- SignallingRef[IO, Boolean](false)
  yield FS2AppenderComps(topic, signal, d)

abstract class FS2Appender[E](comps: FS2AppenderComps[E]) extends AppenderBase[E]:
  val d: Dispatcher[IO] = comps.d
  val topic: Topic[IO, Option[E]] = comps.topic
  val source: Stream[IO, E] = topic
    .subscribe(maxQueued = 10)
    .flatMap(opt => opt.map(e => Stream(e)).getOrElse(Stream.empty))
    .interruptWhen(comps.signal)

  override def stop(): Unit =
    super.stop()
    d.unsafeRunAndForget(comps.signal.set(true))
