package controllers

import akka.actor.Props
import com.malliina.logstreams.ws.{LatestMediator, SourceMediator, SourceSockets}
import com.malliina.play.ActorExecution
import com.malliina.play.auth.Authenticator
import com.malliina.play.models.Username
import com.malliina.play.ws._

class SocketsBundle(listenerAuth: Authenticator[Username],
                    sourceAuth: Authenticator[Username],
                    deps: ActorExecution) {
  val logs = listeners(Props(new ReplayMediator(1000)), listenerAuth)
  val admins = listeners(Props(new LatestMediator), listenerAuth)
  val sourceProps = Props(new SourceMediator(logs.mediator, admins.mediator))
  val sources = new SourceSockets(sourceProps, sourceAuth, deps)

//  implicit val ec = deps.executionContext
  //  val event = AppLogEvent(LogSource(AppName("app"), "remote"), TestData.dummyEvent("jee"))
  //  deps.actorSystem.scheduler.schedule(1.seconds, 1.second, logs.mediator, Mediator.Broadcast(Json.toJson(AppLogEvents(Seq(event)))))

  def listenerSocket = logs.newSocket

  def adminSocket = admins.newSocket

  def sourceSocket = sources.newSocket

  def listeners[U](props: Props, auth: Authenticator[U]): MediatorSockets[U] =
    new MediatorSockets[U](props, auth, deps)
}
