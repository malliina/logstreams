package it

import com.malliina.http.{Errors, FullUrl, HttpHeaders}
import io.circe.Json
import io.circe.syntax.EncoderOps

class ServerPostTests extends TestServerSuite:
  def port = server().server.address.getPort

  http.test("Invalid token returns token_expired error key in error response"): client =>
    val url = FullUrl("http", s"localhost:$port", "/sources/logs")
    val task = client
      .postJson(
        url,
        Json.obj("k" -> "v".asJson),
        Map(HttpHeaders.Authorization -> s"Bearer expired", "Csrf-Token" -> "nocheck")
      )
    task.map: res =>
      assertEquals(res.code, 401)
      val errsOpt = res.parse[Errors].toOption
      assert(errsOpt.isDefined)
      assertEquals(errsOpt.get.errors.head.key, "token_expired")
