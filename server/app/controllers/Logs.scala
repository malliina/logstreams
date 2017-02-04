package controllers

import akka.actor.ActorSystem
import akka.stream.Materializer
import ch.qos.logback.classic.Level
import com.malliina.app.AppMeta
import com.malliina.logbackrx.LogEvent
import com.malliina.logstreams.auth.UserService
import com.malliina.logstreams.models.{AppLogEvent, AppLogEvents, AppName, LogSource}
import com.malliina.logstreams.tags.Htmls
import com.malliina.logstreams.{ListenerActor, SourceActor}
import com.malliina.play.auth.Auth
import com.malliina.play.controllers.BaseController
import com.malliina.rx.BoundedReplaySubject
import play.api.Logger
import play.api.http.Writeable
import play.api.libs.json.{JsValue, Json}
import play.api.libs.streams.ActorFlow
import play.api.mvc._
import rx.lang.scala.{Observable, Observer}

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

object Logs {
  private val log = Logger(getClass)
}

class Logs(htmls: Htmls, oauth: LogAuth, users: UserService, actorSystem: ActorSystem, mat: Materializer)
  extends Controller {

  implicit val as = actorSystem
  implicit val m = mat
  implicit val ec = mat.executionContext

  val replaySize = 10
  val messages = BoundedReplaySubject[AppLogEvent](replaySize).toSerialized
  val events: Observable[AppLogEvents] = messages.tumblingBuffer(100.millis).filter(_.nonEmpty).map(AppLogEvents.apply)
  val eventSink: Observer[AppLogEvent] = messages

  // HTML

  def index = navigate(htmls.logs)

  def sources = navigate(htmls.servers)

  def navigate[C: Writeable](content: => C): EssentialAction =
    oauth.withAuth(_ => Ok(content))

  // WebSockets

  def listenerSocket = WebSocket.acceptOrResult[Any, JsValue] { req =>
    val dummy = testEvent(dummyEvent("listener connected - this is a very long line blahblahblah jdhsfjkdshf dskhf dskgfdsgfj dsgfdsgfk gdsfkghdskufhdsku f kdsf kdshfkhdskfuhdskufhdskhfkdshfkjdshfkhdsf sduhfdskhfdkshfds fds fdshfkdshfkdshfksdhf dskfhdskfh"))
    eventSink onNext dummy
    eventSink onNext testEvent(failEvent("test fail"))

    def authorizedFlow = ActorFlow.actorRef(out =>
      ListenerActor.props(out, req, events))

    oauth.authenticateSocket(req) map { maybeUser =>
      maybeUser
        .map(_ => Right(authorizedFlow))
        .getOrElse(Left(Unauthorized))
    }
  }

  def sourceSocket = WebSocket.acceptOrResult[JsValue, JsValue] { req =>
    Auth.basicCredentials(req) map { creds =>
      def authorizedFlow = ActorFlow.actorRef(
        out => SourceActor.props(out, creds.username, req, eventSink))

      users.isValid(creds) map { isValid =>
        if (isValid) Right(authorizedFlow)
        else Left(Unauthorized)
      }
    } getOrElse {
      Future.successful(Left(Unauthorized))
    }
  }

  // Utils

  def ping = Action(BaseController.NoCacheOk(Json.toJson(AppMeta.ThisApp)))

  // Dev purposes

  def dummyEvent(msg: String) = LogEvent(
    System.currentTimeMillis(),
    "now",
    msg,
    getClass.getName.stripSuffix("$"),
    "this thread",
    Level.INFO,
    None)

  def failEvent(msg: String) = {
    LogEvent(
      System.currentTimeMillis(),
      "now!",
      msg,
      getClass.getName.stripSuffix("$"),
      Thread.currentThread().getName,
      Level.ERROR,
      Option(new Exception("boom").getStackTraceString)
    )
  }

  def testEvent(e: LogEvent) = AppLogEvent(LogSource(AppName("test"), "localhost"), e)
}
