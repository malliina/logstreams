package com.malliina.logback.fs2

import cats.effect.unsafe.IORuntime
import cats.effect.{Concurrent, IO}
import ch.qos.logback.core.AppenderBase
import fs2.Stream
import fs2.concurrent.{SignallingRef, Topic}

class FS2IOAppender[E]()(implicit rt: IORuntime) extends FS2Appender[E] {
  override def append(eventObject: E): Unit =
    topic.publish1(Option(eventObject)).unsafeRunAndForget()
}

abstract class FS2Appender[E](implicit rt: IORuntime) extends AppenderBase[E] {
  val topic = Topic[IO, Option[E]].unsafeRunSync()
  val signal = SignallingRef[IO, Boolean](false).unsafeRunSync()
  val source: Stream[IO, E] = topic
    .subscribe(maxQueued = 10)
    .flatMap(opt => opt.map(e => Stream(e)).getOrElse(Stream.empty))
    .interruptWhen(signal)

  def close(): Unit = signal.set(true).unsafeRunSync()
}
