package com.malliina.logstreams.client

import com.malliina.http.FullUrl
import com.neovisionaries.ws.client.WebSocketException
import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class SocketTests extends AnyFunSuite {
  def failSocket = new SocketClient(FullUrl("http", "kjdhfkdshfds.com", "/blaa"), null, Nil)

  test("network failure fails with WebSocketException") {
    val socket = failSocket
    intercept[WebSocketException] {
      await(socket.initialConnection)
    }
    socket.close()
  }

  ignore("sending to a closed socket fails") {
    val socket = failSocket
    intercept[WebSocketException] {
      await(socket.initialConnection)
    }
    // This does not throw, perhaps due to async
    socket send "hey hey"
    Thread sleep 1000
    socket.close()
  }

  def await[T](f: Future[T]) = Await.result(f, 5.seconds)
}
