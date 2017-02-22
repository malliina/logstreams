package controllers

import akka.actor.Props
import com.malliina.logstreams.SourceMediatorActor
import com.malliina.logstreams.ws.SourceSockets
import com.malliina.play.ActorContext
import com.malliina.play.auth.Authenticator
import com.malliina.play.models.Username
import com.malliina.play.ws._

class SocketsBundle(listenerAuth: Authenticator[Username],
                    sourceAuth: Authenticator[Username],
                    deps: ActorContext) {
  val logListeners = listeners(Mediator.props(), listenerAuth)
  val sourceListeners = listeners(Mediator.props(), listenerAuth)
  val sourceProps = Props(new SourceMediatorActor(logListeners.mediator, sourceListeners.mediator))
  val sources = new SourceSockets(sourceProps, sourceAuth, deps)

  def listenerSocket = logListeners.newSocket

  def adminSocket = sourceListeners.newSocket

  def sourceSocket = sources.newSocket

  def listeners[U](props: Props, auth: Authenticator[U]): SimpleSockets[U] =
    new SimpleSockets[U](props, auth, deps)
}
