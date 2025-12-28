package com.malliina.logstreams.http4s

import cats.effect.IO
import com.malliina.http.UrlSyntax.http
import com.malliina.http.io.HttpClientIO
import fs2.Stream
import io.circe.Json

import scala.concurrent.duration.DurationInt

class ServerTests extends munit.CatsEffectSuite:
  val http = ResourceFunFixture(HttpClientIO.resource[IO])

  // https://gitter.im/functional-streams-for-scala/fs2?at=5d83c2b62438b53a64dec29a
  test("running side effects in a Resource".ignore):
    val cache = Stream.fixedRate[IO](2.seconds) >> Stream.eval(IO(println("cached")))
    val hm = (Stream.emit(()) concurrently cache).compile.resource.lastOrError.use: _ =>
      IO(println("main started ")) >> IO.sleep(5.seconds) >> IO(println("main ended"))
    hm.unsafeRunSync()

  http.test("Obtain token".ignore): client =>
    client
      .postJson(http"localhost:9001/sources/auth", Json.obj(), Map("Csrf-Token" -> "nocheck"))
      .map: res =>
        println(res.body)
        assertEquals(res.code, 200)
