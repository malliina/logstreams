package com.malliina.logstreams.ws

import com.malliina.logstreams.models._
import com.malliina.logstreams.ws.SourceMediatorActor.{SourceInfo, SourceJoined}
import com.malliina.play.ws.{ClientActor, ClientContext}
import play.api.libs.json.{JsResult, JsValue, Json}

class SourceActor(app: AppName, ctx: ClientContext) extends ClientActor(ctx) {
  val src = LogSource(app, address)

  override def preStart(): Unit = {
    super.preStart()
    mediator ! SourceJoined(SourceInfo(src, ctx.out))
  }

  override def transform(message: JsValue): JsResult[JsValue] =
    message.validate[LogEvents].map(les => Json.toJson(toAppEvents(les)))

  def toAppEvents(logEvents: LogEvents) = {
    val appEvents = logEvents.events.map(event => AppLogEvent(src, event))
    AppLogEvents(appEvents)
  }
}
