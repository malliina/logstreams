package controllers

import akka.actor.ActorSystem
import akka.stream.Materializer
import ch.qos.logback.classic.Level
import com.malliina.logbackrx.LogEvent
import com.malliina.logstreams.tags.Htmls
import com.malliina.logstreams.{ListenerActor, SourceActor}
import com.malliina.rx.BoundedReplaySubject
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.streams.ActorFlow
import play.api.mvc._
import rx.lang.scala.{Observable, Observer}

object Logs {
  private val log = Logger(getClass)
}

class Logs(htmls: Htmls)(implicit actorSystem: ActorSystem, mat: Materializer)
  extends BaseController {

  implicit val ec = mat.executionContext

  val replaySize = 10
  val messages = BoundedReplaySubject[LogEvent](replaySize).toSerialized
  val events: Observable[LogEvent] = messages
  val eventSink: Observer[LogEvent] = messages

  def dummyEvent(msg: String) = LogEvent(
    System.currentTimeMillis(),
    "now",
    msg,
    getClass.getName.stripSuffix("$"),
    "this thread",
    Level.INFO,
    None)

  def failEvent(msg: String) = LogEvent(
    System.currentTimeMillis(),
    "now!",
    msg,
    getClass.getName.stripSuffix("$"),
    Thread.currentThread().getName,
    Level.ERROR,
    Option(new Exception("boom").getStackTraceString)
  )

  // HTML

  def index = okAction { _ =>
    htmls.logs
  }

  def sources = okAction { _ =>
    htmls.servers
  }

  // Websockets

  def listenerSocket = WebSocket.accept[Any, JsValue] { req =>
    eventSink onNext dummyEvent("listener connected - this is a very long line blahblahblah jdhsfjkdshf dskhf dskgfdsgfj dsgfdsgfk gdsfkghdskufhdsku f kdsf kdshfkhdskfuhdskufhdskhfkdshfkjdshfkhdsf sduhfdskhfdkshfds fds fdshfkdshfkdshfksdhf dskfhdskfh")
    eventSink onNext failEvent("test fail")
    ActorFlow.actorRef(out => ListenerActor.props(out, req, events))
  }

  def sourceSocket = WebSocket.accept[JsValue, JsValue] { req =>
    ActorFlow.actorRef(out => SourceActor.props(out, req, eventSink))
  }
}
