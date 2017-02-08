package com.malliina.logstreams

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import com.malliina.logstreams.MediatorActor._
import com.malliina.logstreams.models._
import com.malliina.play.models.Username
import play.api.http.HeaderNames
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.RequestHeader

import scala.concurrent.duration.DurationInt

case class Listener(out: ActorRef, req: RequestHeader, username: Username, mediator: ActorRef)

object MediatorActor {

  def props() = Props(new MediatorActor)

  case class SourceInfo(source: LogSource, out: ActorRef)

  trait ControlMessage

  case class SourceJoined(source: SourceInfo) extends ControlMessage

  case class SourceLeft(source: SourceInfo) extends ControlMessage

  case class SourceViewerJoined(admin: ActorRef) extends ControlMessage

  case class LogViewerJoined(out: ActorRef) extends ControlMessage

  case class LogViewerLeft(out: ActorRef) extends ControlMessage

  case class Events(event: AppLogEvents) extends ControlMessage

}

class MediatorActor extends Actor with ActorLogging {
  var sources: Set[SourceInfo] = Set.empty
  // The ActorRef is ListenerActor.out
  var logViewers: Set[ActorRef] = Set.empty
  var sourceViewers: Set[ActorRef] = Set.empty

  import context.dispatcher

  val cancellable = context.system.scheduler.schedule(1.second, 5.seconds)(self ! devMessage)

  def devMessage = {
    val e =
      if (math.random < 0.5) TestData.dummyEvent("hello, world!")
      else TestData.failEvent("failed!")
    Events(AppLogEvents(Seq(TestData.testEvent(e))))
  }

  override def receive = {
    case Events(events) =>
      val json = Json.toJson(events)
      logViewers foreach { out => out ! json }
    case SourceJoined(source) =>
      context watch source.out
      sources += source
      updateSourceViewers()
    case SourceLeft(source) =>
      sources -= source
      updateSourceViewers()
    case LogViewerJoined(listener) =>
      context watch listener
      logViewers += listener
    case LogViewerLeft(listener) =>
      logViewers -= listener
    case SourceViewerJoined(out) =>
      sourceViewers += out
    case Terminated(actor) =>
      logViewers -= actor
      sources.find(_.out == actor) foreach { src =>
        sources -= src
        updateSourceViewers()
      }
      log info s"Log viewers: ${logViewers.size}, source viewers: ${sourceViewers.size}, sources: ${sources.size}"
  }

  def updateSourceViewers() = {
    val srcs = LogSources(sources.map(_.source).toSeq)
    val json = Json.toJson(srcs)
    sourceViewers foreach { out => out ! json }
  }

  override def postStop() = {
    cancellable.cancel()
  }
}

object SourceActor {
  def props(ctx: Listener) = Props(new SourceActor(ctx))
}

/** A connected event source.
  */
class SourceActor(ctx: Listener) extends JsonActor(ctx.req) {
  val src = LogSource(AppName(ctx.username.name), address)

  override def preStart() = ctx.mediator ! SourceJoined(SourceInfo(src, ctx.out))

  override def onMessage(message: JsValue): Unit = push(message, ctx.req)

  private def push(message: JsValue, req: RequestHeader): Unit = {
    message.validate[LogEvents]
      .map(onEvents)
      .recoverTotal(_ => log.error(s"Unsupported server message from $address: '$message'."))
  }

  private def onEvents(events: LogEvents) = {
    val appEvents = events.events.map(logEvent => AppLogEvent(src, logEvent))
    ctx.mediator ! Events(AppLogEvents(appEvents))
  }
}

object SourceViewerActor {
  def props(ctx: Listener) = Props(new SourceViewerActor(ctx))
}

class SourceViewerActor(ctx: Listener) extends JsonActor(ctx.req) {
  override def preStart() = ctx.mediator ! SourceViewerJoined(ctx.out)
}

object LogViewerActor {
  def props(ctx: Listener) = Props(new LogViewerActor(ctx))
}

/** A connected listener, typically a browser.
  *
  * Send a message to `ctx.out` to send it to the listener.
  */
class LogViewerActor(ctx: Listener) extends JsonActor(ctx.req) {
  override def preStart() = ctx.mediator ! LogViewerJoined(ctx.out)
}

class JsonActor(req: RequestHeader) extends Actor with ActorLogging {
  override def receive: Receive = {
    case json: JsValue => onMessage(json)
  }

  def onMessage(message: JsValue): Unit =
    log.info(s"Client $address says: $message")

  def address: String = req.headers.get(HeaderNames.X_FORWARDED_FOR) getOrElse req.remoteAddress
}
