package controllers

import akka.actor.ActorSystem
import akka.stream.Materializer
import ch.qos.logback.classic.Level
import com.malliina.logbackrx.LogEvent
import com.malliina.logstreams.{ClientActor, ServerActor}
import com.malliina.rx.BoundedReplaySubject
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.streams.ActorFlow
import play.api.mvc._
import rx.lang.scala.{Observable, Observer}
import views.html

class Logs()(implicit actorSystem: ActorSystem, mat: Materializer) extends BaseController {
  val log = Logger(getClass)

  implicit val ec = mat.executionContext

  val replaySize = 10
  val messages = BoundedReplaySubject[LogEvent](replaySize).toSerialized
  val events: Observable[LogEvent] = messages
  val eventSink: Observer[LogEvent] = messages

  def dummyEvent = LogEvent(System.currentTimeMillis(), "now", "message", "logger", "this thread", Level.INFO, None)

  def index = okAction(req => html.index(routes.Logs.webClient().webSocketURL()(req)))

  def servers = okAction(req => html.servers(routes.Logs.serverClient().webSocketURL()(req)))

  def webClient = WebSocket.accept[Any, JsValue] { req =>
    ActorFlow.actorRef(out => ClientActor.props(out, req, events))
  }

  def serverClient = WebSocket.accept[JsValue, JsValue] { req =>
    ActorFlow.actorRef(out => ServerActor.props(out, req, eventSink))
  }
}
