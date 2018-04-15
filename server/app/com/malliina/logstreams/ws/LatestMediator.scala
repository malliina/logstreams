package com.malliina.logstreams.ws

import akka.actor.{ActorRef, Props}
import com.malliina.play.ws.Mediator
import com.malliina.play.ws.Mediator.Broadcast
import play.api.libs.json.JsValue

object LatestMediator {
  def props() = Props(new LatestMediator)
}

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
