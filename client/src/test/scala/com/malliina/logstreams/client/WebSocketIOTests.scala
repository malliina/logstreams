package com.malliina.logstreams.client

import munit.FunSuite
import fs2.Stream
import cats.effect.IO

class WebSocketIOTests extends FunSuite {
  test("Stream.repeat") {
    val a: List[String] = Stream
      .eval(IO(System.currentTimeMillis()))
      .flatMap(_ => Stream("a", "b"))
      .repeat
      .take(5)
      .compile
      .toList
      .unsafeRunSync()
    assertEquals(a, List("a", "b", "a", "b", "a"))
  }
}
