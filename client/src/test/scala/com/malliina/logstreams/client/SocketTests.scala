package com.malliina.logstreams.client

import cats.effect.IO
import cats.effect.std.Dispatcher
import com.malliina.http.FullUrl
import com.malliina.http.io.{HttpClientIO, WebSocketF}
import cats.effect.unsafe.implicits.global
import ch.qos.logback.classic.Level
import com.malliina.logback.LogEvent

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class SocketTests extends munit.CatsEffectSuite:
  test("connect".ignore) {
    val headers: List[KeyValue] = List(HttpUtil.basicAuth("test", "changeme"))
//    val url = FullUrl.ws("localhost:9000", "/ws/sources")
    val url = FullUrl.wss("logs.malliina.com", "/ws/sources")
    val s = for
      http <- HttpClientIO.resource[IO]
      socket <- WebSocketF.build[IO](url, headers.map(kv => kv.key -> kv.value).toMap, http.client)
    yield socket
    val resource = Dispatcher.parallel[IO]
    val (d, dc) = resource.allocated[Dispatcher[IO]].unsafeRunSync()
    val (socket, closer) = d.unsafeRunSync(s.allocated[WebSocketF[IO]])
    d.unsafeRunAndForget(socket.events.compile.drain)
    Thread.sleep(2000)
    (1 to 10).map { i =>
      Thread.sleep(5000)
      d.unsafeRunSync(socket.send(LogEvents(Seq(dummyEvent(s"hej $i")))))
      println(s"Sent $i")
    }
  }

//  test("network failure fails with WebSocketException".ignore) {
//    val socket = failSocket
//    intercept[WebSocketException] {
//      await(socket.initialConnection)
//    }
//    socket.close()
//  }
//
//  test("sending to a closed socket fails".ignore) {
//    val socket = failSocket
//    intercept[WebSocketException] {
//      await(socket.initialConnection)
//    }
//    // This does not throw, perhaps due to async
//    socket.send("hey hey")
//    Thread sleep 1000
//    socket.close()
//  }

  def dummyEvent(msg: String) = LogEvent(
    System.currentTimeMillis(),
    "now",
    msg,
    getClass.getName.stripSuffix("$"),
    "this thread",
    Level.INFO,
    None
  )
