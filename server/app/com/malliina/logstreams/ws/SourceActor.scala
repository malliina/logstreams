package com.malliina.logstreams.ws

import akka.actor.Props
import com.malliina.logstreams.models._
import com.malliina.logstreams.ws.SourceActor.log
import com.malliina.logstreams.ws.SourceMediator.{SourceInfo, SourceJoined}
import com.malliina.play.ws.{ClientActor, ClientContext}
import play.api.Logger
import play.api.libs.json.JsValue

object SourceActor {
  private val log = Logger(getClass)

  def props(app: AppName, ctx: ClientContext) = Props(new SourceActor(app, ctx))
}

class SourceActor(app: AppName, ctx: ClientContext) extends ClientActor(ctx) {
  val src = LogSource(app, address)

  override def preStart(): Unit = {
    super.preStart()
    mediator ! SourceJoined(SourceInfo(src, ctx.out))
  }

  override def onMessage(message: JsValue): Unit = {
    message.validate[LogEvents].map(toAppEvents).fold(
      error => log error s"Validation of '$message' failed. $error",
      events => mediator ! events
    )
  }

  def toAppEvents(logEvents: LogEvents): AppLogEvents = {
    val appEvents = logEvents.events.map(event => AppLogEvent(src, event))
    AppLogEvents(appEvents)
  }
}
