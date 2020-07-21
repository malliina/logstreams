package com.malliina.logstreams.client

import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpHeader.ParsingResult.Ok
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.model.{HttpHeader, StatusCodes, Uri}
import akka.pattern.after
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{KillSwitches, Materializer}
import akka.{Done, NotUsed}
import com.malliina.http.FullUrl
import com.malliina.logstreams.client.WebSocketClient.log
import play.api.libs.json.{JsValue, Json, Writes}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future, Promise}

object WebSocketClient {
  private val log = Logging(getClass)

  def apply(
    url: FullUrl,
    headers: List[KeyValue],
    as: ActorSystem,
    mat: Materializer
  ): WebSocketClient =
    new WebSocketClient(url, headers)(as, mat)
}

class WebSocketClient(url: FullUrl, headers: List[KeyValue])(implicit
  as: ActorSystem,
  mat: Materializer
) extends Closeable {

  implicit val ec: ExecutionContext = mat.executionContext
  val validHeaders = headers.map(kv => HttpHeader.parse(kv.key, kv.value)).collect {
    case Ok(header, _) => header
  }
  val scheduler = as.scheduler
  private val enabled = new AtomicBoolean(true)
  val reconnectInterval = 2.seconds
  private val initialConnectionPromise = Promise[WebSocketUpgradeResponse]()
  val initialConnection = initialConnectionPromise.future
  val switch = KillSwitches.shared(s"WebSocket-$url")

  def connect[T: Writes](out: Source[T, NotUsed]): Future[Done] =
    connectInOut(Sink.ignore, out)

  def connectJson[T: Writes](
    in: Sink[JsValue, Future[Done]],
    out: Source[T, NotUsed]
  ): Future[Done] = {
    val incomingSink = in.contramap[Message] {
      case BinaryMessage.Strict(data) => Json.parse(data.iterator.asInputStream)
      case TextMessage.Strict(text)   => Json.parse(text)
      case other                      => throw new Exception(s"Unsupported message: '$other'.")
    }
    connectInOut(incomingSink, out)
  }

  def connectInOut[T: Writes](
    in: Sink[Message, Future[Done]],
    out: Source[T, NotUsed]
  ): Future[Done] = {
    log.info(s"Connecting to '$url'...")
    val messageSource = out.map(t => TextMessage(Json.stringify(Json.toJson(t))))
    val flow = Flow.fromSinkAndSourceMat(in, messageSource)(Keep.left).via(switch.flow)
    val (upgrade, closed) =
      Http().singleWebSocketRequest(WebSocketRequest(Uri(url.url), validHeaders), flow)
    upgrade.map { up =>
      initialConnectionPromise.trySuccess(up)
      val code = up.response.status
      if (code == StatusCodes.SwitchingProtocols) {
        log.info(s"WebSocket connected to '$url'.")
      } else {
        log.error(s"WebSocket connection attempt failed with code ${code.intValue()}.")
      }
    }
    closed
      .recover {
        case t =>
          log.warn(s"WebSocket disconnected.", t)
          Done
      }
      .flatMap { done =>
        if (enabled.get()) {
          log.warn(s"WebSocket disconnected. Reconnecting after $reconnectInterval...")
          after(reconnectInterval, scheduler)(connectInOut(in, out))
        } else {
          log.info(s"WebSocket disconnected. No more reconnects.")
          Future.successful(done)
        }
      }
  }

  def close(): Unit = {
    enabled.set(false)
    switch.shutdown()
  }
}
