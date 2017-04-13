package com.malliina.logstreams.ws

import akka.actor.Props
import com.malliina.logstreams.models.AppName
import com.malliina.play.ActorExecution
import com.malliina.play.auth.Authenticator
import com.malliina.play.models.Username
import com.malliina.play.ws._

class SourceSockets(mediatorProps: Props,
                    sourceAuth: Authenticator[Username],
                    ctx: ActorExecution)
  extends Sockets[Username](sourceAuth, ctx) {
  val mediator = actorSystem.actorOf(mediatorProps)

  override def props(conf: ActorConfig[Username]): Props = {
    val app = AppName(conf.user.name)
    val client = MediatorClient(conf, mediator)
    Props(new SourceActor(app, client))
  }
}
