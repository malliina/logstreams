package com.malliina.logstreams.ws

import akka.actor.{ActorRef, Props}
import com.malliina.logstreams.models.AppName
import com.malliina.play.ActorContext
import com.malliina.play.auth.Authenticator
import com.malliina.play.models.Username
import com.malliina.play.ws.{SimpleClientContext, SimpleSockets}
import play.api.mvc.RequestHeader

class SourceSockets(mediatorProps: Props,
                    sourceAuth: Authenticator[Username],
                    deps: ActorContext)
  extends SimpleSockets[Username](mediatorProps, sourceAuth, deps) {

  override def props(out: ActorRef, user: Username, rh: RequestHeader) = {
    val app = AppName(user.name)
    val ctx = SimpleClientContext(out, rh, mediator)
    Props(new SourceActor(app, ctx))
  }
}
