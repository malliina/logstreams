package com.malliina.logstreams.client

import cats.effect.IO
import cats.effect.unsafe.{IORuntime, IORuntimeConfig, Scheduler}
import com.malliina.http.{OkClient, OkHttpBackend}
import com.malliina.http.io.WebSocketIO
import com.malliina.logstreams.client.FS2Appender.ec
import io.circe.syntax._

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

object FS2Appender {
  val executor = Executors.newCachedThreadPool()
  val ec: ExecutionContext = ExecutionContext.fromExecutor(executor)

  def customRuntime: IORuntime = {
    val (scheduler, finalizer) = IORuntime.createDefaultScheduler()
    IORuntime(ec, ec, scheduler, finalizer, IORuntimeConfig())
  }
}

class FS2Appender(rt: IORuntime, http: OkHttpBackend) extends SocketAppender[WebSocketIO](rt) {
  def this() = this(FS2Appender.customRuntime, OkClient.default)
  implicit val runtime: IORuntime = rt
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
          WebSocketIO(url, headers.map(kv => kv.key -> kv.value).toMap, http.client)
            .unsafeRunSync()
        socket.events.compile.drain.unsafeRunAndForget()
        client = Option(socket)
        val task = logEvents
          .map(e => socket.send(e))
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

  override def stop(): Unit = {
    rt.shutdown()
    FS2Appender.executor.shutdown()
    http.close()
  }
}
