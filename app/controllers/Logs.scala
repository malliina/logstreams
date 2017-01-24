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

  def dummyEvent = LogEvent(System.currentTimeMillis(), "now", "message", "logger", "this thread", Level.INFO, None)

  // HTML

  def index = okAction { _ =>
    htmls.index
  }

  def sources = okAction { _ =>
    htmls.servers
  }

  // Websockets

  def webClient = WebSocket.accept[Any, JsValue] { req =>
    ActorFlow.actorRef(out => ListenerActor.props(out, req, events))
  }

  def serverClient = WebSocket.accept[JsValue, JsValue] { req =>
    ActorFlow.actorRef(out => SourceActor.props(out, req, eventSink))
  }
}
