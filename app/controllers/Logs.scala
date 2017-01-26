package controllers

import akka.actor.ActorSystem
import akka.stream.Materializer
import ch.qos.logback.classic.Level
import com.malliina.logbackrx.LogEvent
import com.malliina.logstreams.auth.UserService
import com.malliina.logstreams.tags.Htmls
import com.malliina.logstreams.{ListenerActor, SourceActor}
import com.malliina.play.auth.Auth
import com.malliina.rx.BoundedReplaySubject
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.streams.ActorFlow
import play.api.mvc._
import rx.lang.scala.{Observable, Observer}

import scala.concurrent.Future

object Logs {
  private val log = Logger(getClass)
}

class Logs(htmls: Htmls, oauth: OAuthCtrl, users: UserService)(implicit actorSystem: ActorSystem, mat: Materializer)
  extends BaseController {

  implicit val ec = mat.executionContext

  val replaySize = 10
  val messages = BoundedReplaySubject[LogEvent](replaySize).toSerialized
  val events: Observable[LogEvent] = messages
  val eventSink: Observer[LogEvent] = messages

  // HTML

  def index = oauth.navigate { _ =>
    htmls.logs
  }

  def sources = oauth.navigate { _ =>
    htmls.servers
  }

  // WebSockets

  def listenerSocket = WebSocket.acceptOrResult[Any, JsValue] { req =>
    eventSink onNext dummyEvent("listener connected - this is a very long line blahblahblah jdhsfjkdshf dskhf dskgfdsgfj dsgfdsgfk gdsfkghdskufhdsku f kdsf kdshfkhdskfuhdskufhdskhfkdshfkjdshfkhdsf sduhfdskhfdkshfds fds fdshfkdshfkdshfksdhf dskfhdskfh")
    eventSink onNext failEvent("test fail")

    def authorizedFlow = ActorFlow.actorRef(out => ListenerActor.props(out, req, events))

    oauth.authenticateFromSession(req) map { maybeUser =>
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

  def dummyEvent(msg: String) = LogEvent(
    System.currentTimeMillis(),
    "now",
    msg,
    getClass.getName.stripSuffix("$"),
    "this thread",
    Level.INFO,
    None)

  def failEvent(msg: String) = LogEvent(
    System.currentTimeMillis(),
    "now!",
    msg,
    getClass.getName.stripSuffix("$"),
    Thread.currentThread().getName,
    Level.ERROR,
    Option(new Exception("boom").getStackTraceString)
  )
}
