package com.malliina.logstreams.client

import cats.effect.unsafe.implicits.global
import cats.effect.IO
import com.malliina.http.OkClient
import com.malliina.http.io.WebSocketIO
import io.circe.syntax._

import scala.concurrent.ExecutionContext

class FS2Appender extends SocketAppender[WebSocketIO] {
  override def start(): Unit = {
    if (getEnabled) {
      val result = for {
        url <- toMissing(endpoint, "endpoint")
        user <- toMissing(username, "username")
        pass <- toMissing(password, "password")
      } yield {
        val headers: List[KeyValue] = List(HttpUtil.basicAuth(user, pass))
        addInfo(s"Connecting to logstreams URL '$url' for Logback...")
        val socket =
          WebSocketIO(url, headers.map(kv => kv.key -> kv.value).toMap, OkClient.okHttpClient)
            .unsafeRunSync()
        socket.events.compile.drain.unsafeRunAndForget()
        client = Option(socket)
        val task = logEvents
          .map(e => socket.send(e.asJson.spaces2))
          .onComplete {
            fs2.Stream
              .eval(IO(addInfo(s"Appender [$name] completed.")))
              .flatMap(_ => fs2.Stream.empty)
          }
          .compile
          .drain
          .unsafeRunAndForget()
        super.start()
      }
      result.left.toOption foreach addError
    } else {
      addInfo("Logstreams client is disabled.")
    }
  }
}
