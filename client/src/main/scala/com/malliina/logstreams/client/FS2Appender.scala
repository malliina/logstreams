package com.malliina.logstreams.client

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.std.Dispatcher
import cats.effect.unsafe.{IORuntime, IORuntimeConfig, Scheduler}
import ch.qos.logback.classic.spi.ILoggingEvent
import com.malliina.http.io.{HttpClientIO, WebSocketIO}
import com.malliina.http.{HttpClient, OkClient, OkHttpBackend}
import com.malliina.logback.fs2.{FS2AppenderComps, LoggingComps}
import com.malliina.logstreams.client.FS2Appender.{ResourceParts, ec}
import fs2.concurrent.{SignallingRef, Topic}
import io.circe.syntax.*

import java.util.concurrent.{ExecutorService, Executors}
import scala.concurrent.ExecutionContext
import concurrent.duration.DurationInt

object FS2Appender:
  val executor: ExecutorService = Executors.newCachedThreadPool()
  val ec: ExecutionContext = ExecutionContext.fromExecutor(executor)

  case class SocketComps(comps: LoggingComps, http: HttpClientIO)

  case class ResourceParts(
    comps: LoggingComps,
    http: HttpClientIO,
    finalizer: IO[Unit]
  )

  def customRuntime: IORuntime =
    val (scheduler, finalizer) = IORuntime.createDefaultScheduler()
    IORuntime(ec, ec, scheduler, finalizer, IORuntimeConfig())

  def dispatched(d: Dispatcher[IO], dispatcherFinalizer: IO[Unit]): ResourceParts =
    val resource = for
      comps <- Resource.eval(FS2AppenderComps.io(d))
      http <- HttpClientIO.resource
    yield SocketComps(comps, http)
    val (comps, finalizer) = d.unsafeRunSync(resource.allocated[SocketComps])
    ResourceParts(comps.comps, comps.http, finalizer >> dispatcherFinalizer)

  def unsafe: ResourceParts =
    val rt = customRuntime
    val (d, finalizer) = Dispatcher[IO].allocated.unsafeRunSync()(rt)
    dispatched(d, finalizer >> IO(rt.shutdown()))

class FS2Appender(
  res: ResourceParts
) extends SocketAppender[WebSocketIO](res.comps):
  def this() = this(FS2Appender.unsafe)
  private var socketClosable: IO[Unit] = IO.unit
  override def start(): Unit =
    if getEnabled then
      val result = for
        url <- toMissing(endpoint, "endpoint")
        user <- toMissing(username, "username")
        pass <- toMissing(password, "password")
      yield
        val headers: List[KeyValue] = List(HttpUtil.basicAuth(user, pass))
        addInfo(s"Connecting to logstreams URL '$url' for Logback...")
        val socketIo: Resource[IO, WebSocketIO] =
          WebSocketIO(url, headers.map(kv => kv.key -> kv.value).toMap, res.http.client)
        val (socket, closer) = d.unsafeRunSync(socketIo.allocated[WebSocketIO])
        client = Option(socket)
        socketClosable = closer
        d.unsafeRunAndForget(socket.events.compile.drain)
        val task: IO[Unit] = logEvents
          .groupWithin(100, 3.seconds)
          .evalMap(es => socket.send(LogEvents(es.toList)))
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
    d.unsafeRunSync(client.map(_.close).getOrElse(IO.unit) >> res.finalizer)
    FS2Appender.executor.shutdown()
