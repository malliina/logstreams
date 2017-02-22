package com.malliina.logstreams.ws

import akka.actor.ActorRef
import com.malliina.play.ws.Mediator
import com.malliina.play.ws.Mediator.Broadcast
import play.api.libs.json.JsValue

class LatestMediator extends Mediator {
  var latestBroadcast: Option[JsValue] = None

  override def receive: Receive = latestReceive orElse super.receive

  def latestReceive: Receive = {
    case Broadcast(message) =>
      latestBroadcast = Option(message)
      clients foreach { out => out ! message }
  }

  override def onJoined(ref: ActorRef) = {
    latestBroadcast foreach { msg => ref ! msg }
  }
}
