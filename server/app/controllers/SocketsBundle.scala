package controllers

import akka.actor.Props
import com.malliina.logstreams.db.StreamsDB
import com.malliina.logstreams.ws.{DatabaseActor, LatestMediator, SourceMediator, SourceSockets}
import com.malliina.play.ActorExecution
import com.malliina.play.auth.Authenticator
import com.malliina.play.models.Username
import com.malliina.play.ws._

class SocketsBundle(listenerAuth: Authenticator[Username],
                    sourceAuth: Authenticator[Username],
                    db: StreamsDB,
                    deps: ActorExecution) {
  val logs = listeners(Props(new ReplayMediator(1000)), listenerAuth)
  val admins = listeners(Props(new LatestMediator), listenerAuth)
  val database = deps.actorSystem.actorOf(DatabaseActor.props(db))
  val sourceProps = Props(new SourceMediator(logs.mediator, admins.mediator, database))
  val sources = new SourceSockets(sourceProps, sourceAuth, deps)

  //  implicit val ec = deps.executionContext
  //  val event = AppLogEvent(LogSource(AppName("test"), "remote"), TestData.dummyEvent("jee"))
  //  val errorEvent = AppLogEvent(LogSource(AppName("test"), "remote"), TestData.failEvent("boom"))
  //  deps.actorSystem.scheduler.schedule(1.seconds, 1.second, sources.mediator, AppLogEvents(Seq(event, errorEvent)))

  def listenerSocket = logs.newSocket

  def adminSocket = admins.newSocket

  def sourceSocket = sources.newSocket

  def listeners[U](props: Props, auth: Authenticator[U]): MediatorSockets[U] =
    new MediatorSockets[U](props, auth, deps)
}
