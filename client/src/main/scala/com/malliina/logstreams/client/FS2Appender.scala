package com.malliina.logstreams.client

import cats.effect.IO
import cats.effect.kernel.{Async, Resource, Sync}
import cats.effect.std.Dispatcher
import cats.effect.unsafe.{IORuntime, IORuntimeConfig, Scheduler}
import cats.syntax.all.{catsSyntaxFlatMapOps, toFunctorOps}
import ch.qos.logback.classic.spi.ILoggingEvent
import com.malliina.http.io.{HttpClientF, HttpClientF2, HttpClientIO, WebSocketF}
import com.malliina.http.{HttpClient, OkClient, OkHttpBackend}
import com.malliina.logback.fs2.{FS2AppenderComps, LoggingComps}
import com.malliina.logstreams.client.FS2Appender.{ResourceParts, ec}
import fs2.concurrent.{SignallingRef, Topic}
import io.circe.syntax.*

import java.util.concurrent.{ExecutorService, Executors}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

object FS2Appender:
  val executor: ExecutorService = Executors.newCachedThreadPool()
  val ec: ExecutionContext = ExecutionContext.fromExecutor(executor)

  case class SocketComps[F[_]](comps: LoggingComps[F], http: HttpClientF2[F])

  case class ResourceParts[F[_]](
    comps: LoggingComps[F],
    http: HttpClientF2[F],
    finalizer: F[Unit]
  )

  private def customRuntime: IORuntime =
    val (scheduler, finalizer) = IORuntime.createDefaultScheduler()
    IORuntime(ec, ec, scheduler, finalizer, IORuntimeConfig())

  private def dispatched(d: Dispatcher[IO], dispatcherFinalizer: IO[Unit]): ResourceParts[IO] =
    val resource = for
      comps <- Resource.eval(FS2AppenderComps.io(d))
      http <- HttpClientIO.resource[IO]
    yield SocketComps(comps, http)
    val (comps, finalizer) = d.unsafeRunSync(resource.allocated[SocketComps[IO]])
    ResourceParts(comps.comps, comps.http, finalizer >> dispatcherFinalizer)

  def unsafe: ResourceParts[IO] =
    val rt = customRuntime
    val (d, finalizer) = Dispatcher.parallel[IO].allocated.unsafeRunSync()(rt)
    dispatched(d, finalizer >> IO(rt.shutdown()))

  def default[F[_]: Async](
    d: Dispatcher[F],
    http: HttpClientF2[F],
    extraHeaders: Map[String, String] = Map.empty
  ): F[FS2AppenderF[F]] =
    FS2AppenderComps.io(d).map { parts =>
      FS2AppenderF(ResourceParts(parts, http, Async[F].unit), extraHeaders)
    }

class FS2Appender(
  res: ResourceParts[IO]
) extends FS2AppenderF[IO](res, Map.empty):
  def this() = this(FS2Appender.unsafe)
  override def stop(): Unit =
    super.stop()
    FS2Appender.executor.shutdown()

class FS2AppenderF[F[_]: Async](
  res: ResourceParts[F],
  extraHeaders: Map[String, String]
) extends SocketAppender[F, WebSocketF[F]](res.comps):
  val F = Sync[F]
  private var socketClosable: F[Unit] = F.unit
  override def start(): Unit =
    if getEnabled then
      val result = for
        url <- toMissing(endpoint, "endpoint")
        user <- toMissing(username, "username")
        pass <- toMissing(password, "password")
      yield
        val headers: List[KeyValue] = List(HttpUtil.basicAuth(user, pass))
        addInfo(s"Connecting to logstreams URL '$url' for Logback...")
        val socketIo: Resource[F, WebSocketF[F]] =
          res.http.socket(url, headers.map(kv => kv.key -> kv.value).toMap ++ extraHeaders)
        val (socket, closer) = d.unsafeRunSync(socketIo.allocated[WebSocketF[F]])
        client = Option(socket)
        socketClosable = closer
        d.unsafeRunAndForget(socket.events.compile.drain)
        val task: F[Unit] = logEvents
          .groupWithin(100, 200.millis)
          .evalMap(es => socket.send(LogEvents(es.toList)))
          .onComplete {
            fs2.Stream
              .eval(F.delay(addInfo(s"Appender [$name] completed.")))
              .flatMap(_ => fs2.Stream.empty)
          }
          .compile
          .drain
        d.unsafeRunAndForget(task)
        super.start()
      result.left.toOption foreach addError
    else addInfo("Logstreams client is disabled.")

  override def stop(): Unit =
    d.unsafeRunSync(client.map(_.close).getOrElse(F.unit) >> res.finalizer >> socketClosable)
