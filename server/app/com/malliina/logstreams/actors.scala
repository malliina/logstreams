package com.malliina.logstreams

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.malliina.logbackrx.LogEvent
import com.malliina.logstreams.models.{AppLogEvent, AppLogEvents, AppName, LogSource}
import com.malliina.play.models.Username
import play.api.http.HeaderNames
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.RequestHeader
import rx.lang.scala.{Observable, Observer}

object SourceActor {
  def props(out: ActorRef, user: Username, req: RequestHeader, next: Observer[AppLogEvent]) =
    Props(new SourceActor(out, user, req, next))
}

/** A connected event source.
  *
  * @param out  the source, unused unless we want to send messages to sources
  * @param req  request
  * @param next sink for messages from the source
  */
class SourceActor(out: ActorRef, user: Username, val req: RequestHeader, next: Observer[AppLogEvent])
  extends JsonActor {

  override def onMessage(message: JsValue): Unit = push(message, req)

  private def push(message: JsValue, req: RequestHeader): Unit = {
    message.validate[LogEvent]
      .map(msg => next.onNext(AppLogEvent(LogSource(AppName(user.name), address), msg)))
      .recoverTotal(_ => log.error(s"Unsupported server message from $address: '$message'."))
  }
}

object ListenerActor {
  def props(out: ActorRef, req: RequestHeader, next: Observable[AppLogEvents]) =
    Props(new ListenerActor(out, req, next))
}

/** A connected listener, typically a browser.
  *
  * Send a message to `out` to send it to the listener.
  *
  * @param out  client
  * @param req  request
  * @param next event source
  */
class ListenerActor(out: ActorRef, val req: RequestHeader, next: Observable[AppLogEvents])
  extends JsonActor {

  val subscription = next.subscribe(
    event => out ! Json.toJson(event),
    err => log.error("Log queue failed.", err),
    () => log.info("Log queue completed.")
  )

  override def onMessage(message: JsValue): Unit =
    log.info(s"Client $address says: $message")

  override def postStop(): Unit = {
    super.postStop()
    subscription.unsubscribe()
    log.info(s"Unsubscribed client $address")
  }
}

trait JsonActor extends Actor with ActorLogging {
  def req: RequestHeader

  def onMessage(message: JsValue): Unit

  override def receive: Receive = {
    case json: JsValue => onMessage(json)
  }

  def address: String = req.headers.get(HeaderNames.X_FORWARDED_FOR) getOrElse req.remoteAddress
}
