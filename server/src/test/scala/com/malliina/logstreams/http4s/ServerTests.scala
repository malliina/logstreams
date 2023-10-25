package com.malliina.logstreams.http4s

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.Stream
import munit.FunSuite

import scala.concurrent.duration.DurationInt

class ServerTests extends FunSuite:
  // https://gitter.im/functional-streams-for-scala/fs2?at=5d83c2b62438b53a64dec29a
  test("running side effects in a Resource".ignore):
    val cache = Stream.fixedRate[IO](2.seconds) >> Stream.eval(IO(println("cached")))
    val hm = (Stream.emit(()) concurrently cache).compile.resource.lastOrError.use { _ =>
      IO(println("main started ")) >> IO.sleep(5.seconds) >> IO(println("main ended"))
    }
    hm.unsafeRunSync()
