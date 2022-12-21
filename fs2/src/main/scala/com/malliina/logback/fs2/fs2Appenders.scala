package com.malliina.logback.fs2

import cats.Monad
import cats.effect.kernel.Async
import cats.effect.{Concurrent, IO, Resource}
import cats.effect.std.Dispatcher
import cats.syntax.all.{toFlatMapOps, toFunctorOps}
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import fs2.Stream
import fs2.concurrent.{SignallingRef, Topic}

class FS2IOAppender[F[_]: Async, E](comps: FS2AppenderComps[F, E]) extends FS2Appender[F, E](comps):
  override def append(eventObject: E): Unit =
    d.unsafeRunAndForget(topic.publish1(Option(eventObject)))

type LoggingComps[F[_]] = FS2AppenderComps[F, ILoggingEvent]

case class FS2AppenderComps[F[_], E](
  topic: Topic[F, Option[E]],
  signal: SignallingRef[F, Boolean],
  d: Dispatcher[F]
)

object FS2AppenderComps:
  def resource[F[_]: Async]: Resource[F, LoggingComps[F]] =
    Dispatcher[F].evalMap(d => io(d))
  def io[F[_]: Concurrent](d: Dispatcher[F]): F[LoggingComps[F]] = for
    topic <- Topic[F, Option[ILoggingEvent]]
    signal <- SignallingRef[F, Boolean](false)
  yield FS2AppenderComps(topic, signal, d)

abstract class FS2Appender[F[_]: Async, E](comps: FS2AppenderComps[F, E]) extends AppenderBase[E]:
  val d: Dispatcher[F] = comps.d
  val topic: Topic[F, Option[E]] = comps.topic
  val source: Stream[F, E] = topic
    .subscribe(maxQueued = 10)
    .flatMap(opt => opt.map(e => Stream(e)).getOrElse(Stream.empty))
    .interruptWhen(comps.signal)

  override def stop(): Unit =
    super.stop()
    d.unsafeRunAndForget(comps.signal.set(true))
