package com.malliina.logstreams.client

import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.effect.unsafe.{IORuntime, IORuntimeConfig, Scheduler}
import ch.qos.logback.classic.spi.ILoggingEvent
import com.malliina.http.{OkClient, OkHttpBackend}
import com.malliina.http.io.WebSocketIO
import com.malliina.logback.fs2.FS2AppenderComps
import com.malliina.logstreams.client.FS2Appender.{ResourceParts, ec}
import fs2.concurrent.{SignallingRef, Topic}
import io.circe.syntax.*
import com.malliina.logback.fs2.LoggingComps
import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

object FS2Appender:
  val executor = Executors.newCachedThreadPool()
  val ec: ExecutionContext = ExecutionContext.fromExecutor(executor)

  case class ResourceParts(comps: LoggingComps, finalizer: IO[Unit], rt: IORuntime)

  def customRuntime: IORuntime =
    val (scheduler, finalizer) = IORuntime.createDefaultScheduler()
    IORuntime(ec, ec, scheduler, finalizer, IORuntimeConfig())

  def unsafe: ResourceParts =
    val rt = customRuntime
    val (comps, finalizer) =
      FS2AppenderComps.resource.allocated[LoggingComps].unsafeRunSync()(rt)
    ResourceParts(comps, finalizer, rt)

class FS2Appender(
  res: ResourceParts,
  http: OkHttpBackend
) extends SocketAppender[WebSocketIO](res.comps):
  def this() = this(FS2Appender.unsafe, OkClient.default)
  override def start(): Unit =
    if getEnabled then
      val result = for
        url <- toMissing(endpoint, "endpoint")
        user <- toMissing(username, "username")
        pass <- toMissing(password, "password")
      yield
        val headers: List[KeyValue] = List(HttpUtil.basicAuth(user, pass))
        addInfo(s"Connecting to logstreams URL '$url' for Logback...")
        val socketIo = WebSocketIO(url, headers.map(kv => kv.key -> kv.value).toMap, http.client)
        val socket = d.unsafeRunSync(socketIo)
        d.unsafeRunAndForget(socket.events.compile.drain)
        client = Option(socket)
        val task = logEvents
          .map(e => socket.send(LogEvents(Seq(e))))
          .onComplete {
            fs2.Stream
              .eval(IO(addInfo(s"Appender [$name] completed.")))
              .flatMap(_ => fs2.Stream.empty)
          }
          .compile
          .drain
        d.unsafeRunAndForget(task)
        super.start()
      result.left.toOption foreach addError
    else addInfo("Logstreams client is disabled.")

  override def stop(): Unit =
    d.unsafeRunSync(res.finalizer)
    res.rt.shutdown()
    FS2Appender.executor.shutdown()
    http.close()
