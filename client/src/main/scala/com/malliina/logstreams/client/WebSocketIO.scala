package com.malliina.logstreams.client

import cats.effect.concurrent.Ref
import cats.effect.{Concurrent, ContextShift, IO, Timer}
import com.malliina.http.FullUrl
import com.malliina.logstreams.client.SocketEvent._
import com.malliina.logstreams.client.WebSocketIO.log
import com.malliina.values.Username
import fs2.Stream
import fs2.concurrent.{SignallingRef, Topic}
import io.circe._
import io.circe.syntax.EncoderOps
import okhttp3._
import okio.ByteString
import org.slf4j.LoggerFactory

import java.io.Closeable
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration.{DurationInt, FiniteDuration}

trait WebSocketOps extends Closeable {
  def open(): Unit
  def send[T: Encoder](message: T): Boolean = sendMessage(message.asJson.noSpaces)
  def sendMessage(s: String): Boolean
}

object WebSocketIO {
  private val log = LoggerFactory.getLogger(getClass)

  def apply(url: FullUrl, headers: Map[String, String], client: OkHttpClient)(implicit
    cs: ContextShift[IO],
    t: Timer[IO]
  ): IO[WebSocketIO] = for {
    topic <- Topic[IO, SocketEvent](SocketEvent.Idle)
    interrupter <- SignallingRef[IO, Boolean](false)
  } yield new WebSocketIO(url, headers, client, topic, interrupter)
}

class WebSocketIO(
  val url: FullUrl,
  headers: Map[String, String],
  client: OkHttpClient,
  topic: Topic[IO, SocketEvent],
  interrupter: SignallingRef[IO, Boolean]
)(implicit
  val cs: ContextShift[IO],
  c: Concurrent[IO],
  t: Timer[IO]
) extends WebSocketOps {
  private val backoffTime: FiniteDuration = 10.seconds
  private val active = new AtomicReference[Option[WebSocket]](None)

  val allEvents: Stream[IO, SocketEvent] = topic.subscribe(10)
  val messages: Stream[IO, String] = allEvents.collect {
    case TextMessage(_, message) => message
  }
  val jsonMessages: Stream[IO, Json] = messages.flatMap { message =>
    parser
      .parse(message)
      .fold(
        err => Stream.raiseError(new Exception(s"Not JSON: '$message'.")),
        ok => Stream.emit(ok)
      )
  }

  private val listener: WebSocketListener = new WebSocketListener {
    override def onClosed(webSocket: WebSocket, code: Int, reason: String) = {
      log.info(s"Closed  socket to '$url'.")
      topic.push(Closed(webSocket, code, reason))
    }
    override def onClosing(webSocket: WebSocket, code: Int, reason: String) = {
      log.info(s"Closing socket to '$url'.")
      topic.push(Closing(webSocket, code, reason))
    }
    override def onFailure(webSocket: WebSocket, t: Throwable, response: Response) = {
      log.warn(s"Socket to '$url' failed.", t)
      topic.push(Failure(webSocket, Option(t), Option(response)))
    }
    override def onMessage(webSocket: WebSocket, text: String) = {
      log.debug(s"Received text '$text'.")
      topic.push(TextMessage(webSocket, text))
    }
    override def onMessage(webSocket: WebSocket, bytes: ByteString) = {
      log.debug(s"Received bytes $bytes")
      topic.push(BytesMessage(webSocket, bytes))
    }
    override def onOpen(webSocket: WebSocket, response: Response) = {
      log.info(s"Opened socket to '$url'.")
      topic.push(Open(webSocket, response))
    }
  }
  val request = requestFor(url, headers).build()
  val connectOnce = IO(client.newWebSocket(request, listener))

  private val connectSocket: IO[WebSocket] = connectOnce.flatMap { socket =>
    IO(active.set(Option(socket))).map(_ => socket)
  }
  private val backoff =
    Stream.eval(IO(log.info(s"Reconnecting to '$url' in $backoffTime..."))).flatMap { _ =>
      Stream.sleep(backoffTime).map(_ => Idle)
    }
  private val untilFailure = allEvents.drop(1).takeWhile {
    case Failure(_, _, _) => false
    case _                => true
  }
  val events: Stream[IO, SocketEvent] = Stream
    .eval(connectSocket)
    .flatMap(_ => untilFailure ++ backoff)
    .repeat
    .interruptWhen(interrupter)

  def messagesAs[T: Decoder]: Stream[IO, T] = jsonMessages.flatMap { json =>
    json
      .as[T]
      .fold(
        err => Stream.raiseError(new Exception(s"Failed to decode '$json'.")),
        ok => Stream.emit(ok)
      )
  }

  def open(): Unit = events.compile.toList.unsafeRunAsyncAndForget()

  def sendMessage(message: String): Boolean = active.get().exists(_.send(message))

  def close(): Unit = {
    log.info(s"Closing socket to '$url'...")
    interrupter.set(true).unsafeRunSync()
    active.get().foreach(_.cancel())
  }

  def requestFor(url: FullUrl, headers: Map[String, String]): Request.Builder =
    headers.foldLeft(new Request.Builder().url(url.url)) {
      case (r, (key, value)) => r.addHeader(key, value)
    }
}
