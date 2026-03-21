package com.malliina.logstreams.http4s

import com.malliina.http.{Errors, FullUrl, HttpHeaders}
import com.malliina.logstreams.auth.{JWT, SecretKey}
import io.circe.Json
import io.circe.syntax.EncoderOps

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.duration.DurationInt

class ServerPostTests extends TestServerSuite:
  def port = server().server.address.getPort

  http.test("Invalid token returns token_expired error key in error response"): client =>
    val jwt = JWT(SecretKey.dev)
    val token =
      jwt.sign(Json.obj("a" -> "b".asJson), 5.minutes, Instant.now().minus(1, ChronoUnit.HOURS))
    val url = FullUrl("http", s"localhost:$port", "/sources/logs")
    val task = client
      .postJson(
        url,
        Json.obj("k" -> "v".asJson),
        Map(HttpHeaders.Authorization -> s"Bearer $token", "Csrf-Token" -> "nocheck")
      )
    task.map: res =>
      assertEquals(res.code, 401)
      val errsOpt = res.parse[Errors].toOption
      assert(errsOpt.isDefined)
      assertEquals(errsOpt.get.errors.head.key, "token_expired")
