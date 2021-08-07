package com.malliina.logstreams

import cats.effect.IO
import fs2.concurrent.Topic

package object client {
  implicit class TopicOps[T](val t: Topic[IO, T]) extends AnyVal {
    def push(message: T): Unit = t.publish1(message).unsafeRunAsyncAndForget()
  }
}