package com.malliina.logstreams.client

import java.net.URL

import com.malliina.http.FullUrl

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class LogstreamTests extends munit.FunSuite {
  test("conn") {
    new URL("https://letsencrypt.org/").openConnection.connect()
  }

  test("connectivity".ignore) {
    val testUser = "user"
    val testPass = "pass"
    val host = "logs.malliina.com"
    val sf = CustomSSLSocketFactory.forHost(host)
    val headers: Seq[KeyValue] = Seq(HttpUtil.basicAuth(testUser, testPass))
    val scheme = if (true) "wss" else "ws"
    val uri = FullUrl(scheme, host, "/ws/sources")
    val socket = new JsonSocket(uri, sf, headers)
    await(socket.initialConnection)
  }

  def await[T](f: Future[T]) = Await.result(f, 10.seconds)
}
