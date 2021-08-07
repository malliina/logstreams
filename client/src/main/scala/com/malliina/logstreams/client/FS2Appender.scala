package com.malliina.logstreams.client

import cats.effect.{ContextShift, IO, Timer}
import com.malliina.http.OkClient
import io.circe.syntax._

import scala.concurrent.ExecutionContext

class FS2Appender extends SocketAppender[WebSocketIO] {
  override def start(): Unit = {
    if (getEnabled) {
      val ec = ExecutionContext.Implicits.global
      implicit val cs: ContextShift[IO] = IO.contextShift(ec)
      implicit val t: Timer[IO] = IO.timer(ec)
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
        socket.events.compile.drain.unsafeRunAsyncAndForget()
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
          .unsafeRunAsyncAndForget()
        super.start()
      }
      result.left.toOption foreach addError
    } else {
      addInfo("Logstreams client is disabled.")
    }
  }
}
