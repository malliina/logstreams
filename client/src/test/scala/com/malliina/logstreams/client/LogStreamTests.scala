package com.malliina.logstreams.client

import java.net.URL

import com.malliina.http.FullUrl
import org.scalatest.FunSuite

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class LogStreamTests extends FunSuite {
  test("conn") {
    new URL("https://letsencrypt.org/").openConnection.connect()
  }

  ignore("connectivity") {
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
