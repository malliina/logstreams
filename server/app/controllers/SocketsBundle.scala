package controllers

import akka.actor.Props
import com.malliina.logstreams.ws.{LatestMediator, SourceMediatorActor, SourceSockets}
import com.malliina.play.ActorExecution
import com.malliina.play.auth.Authenticator
import com.malliina.play.models.Username
import com.malliina.play.ws._

class SocketsBundle(listenerAuth: Authenticator[Username],
                    sourceAuth: Authenticator[Username],
                    deps: ActorExecution) {
  val logs = listeners(Mediator.props(), listenerAuth)
  val admins = listeners(Props(new LatestMediator), listenerAuth)
  val sourceProps = Props(new SourceMediatorActor(logs.mediator, admins.mediator))
  val sources = new SourceSockets(sourceProps, sourceAuth, deps)

  def listenerSocket = logs.newSocket

  def adminSocket = admins.newSocket

  def sourceSocket = sources.newSocket

  def listeners[U](props: Props, auth: Authenticator[U]): MediatorSockets[U] =
    new MediatorSockets[U](props, auth, deps)
}
