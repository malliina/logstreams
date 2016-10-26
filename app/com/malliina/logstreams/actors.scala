package com.malliina.logstreams

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.malliina.logbackrx.LogEvent
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.RequestHeader
import rx.lang.scala.{Observable, Observer}

object ServerActor {
  def props(out: ActorRef, req: RequestHeader, next: Observer[LogEvent]) =
    Props(new ServerActor(out, req, next))
}

class ServerActor(out: ActorRef, val req: RequestHeader, next: Observer[LogEvent]) extends JsonActor {
  override def onMessage(message: JsValue): Unit = push(message, req)

  private def push(message: JsValue, req: RequestHeader): Unit = {
    message.validate[LogEvent]
      .map(next.onNext)
      .recoverTotal(error => log.error(s"Unsupported server message from $address: '$message'."))
  }
}

object ClientActor {
  def props(out: ActorRef, req: RequestHeader, next: Observable[LogEvent]) =
    Props(new ClientActor(out, req, next))
}

class ClientActor(out: ActorRef, val req: RequestHeader, next: Observable[LogEvent]) extends JsonActor {
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

  def address = req.remoteAddress
}
