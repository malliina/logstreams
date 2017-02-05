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
import com.malliina.play.auth.{Auth, BasicCredentials}
import com.malliina.play.controllers.BaseController
import com.malliina.play.http.{AuthedRequest, CookiedRequest}
import com.malliina.play.models.{Password, Username}
import com.malliina.rx.BoundedReplaySubject
import controllers.Logs.{PasswordKey, UsernameKey, LogRequest}
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms.mapping
import play.api.http.Writeable
import play.api.libs.json.{JsValue, Json}
import play.api.libs.streams.ActorFlow
import play.api.mvc._
import rx.lang.scala.{Observable, Observer}

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

object Logs {
  private val log = Logger(getClass)
  type LogRequest = CookiedRequest[AnyContent, AuthedRequest]

  val UsernameKey = "username"
  val PasswordKey = "password"
}

class Logs(htmls: Htmls, oauth: LogAuth, users: UserService, actorSystem: ActorSystem, mat: Materializer)
  extends Controller {

  implicit val as = actorSystem
  implicit val m = mat
  implicit val ec = mat.executionContext

  val replaySize = 500
  val messages = BoundedReplaySubject[AppLogEvent](replaySize).toSerialized
  val events: Observable[AppLogEvents] = messages.tumblingBuffer(100.millis).filter(_.nonEmpty).map(AppLogEvents.apply)
  val eventSink: Observer[AppLogEvent] = messages

  val addUserForm = Form(mapping(
    UsernameKey -> Username.mapping,
    PasswordKey -> Password.mapping
  )(BasicCredentials.apply)(BasicCredentials.unapply))

  // HTML

  def index = navigate(htmls.logs)

  def sources = navigate(htmls.sources)

  def allSources = async { req =>
    users.all().map(us => Ok(htmls.users(us, UserFeedback.flashed(req))))
  }

  def addUser = async { req =>
    addUserForm.bindFromRequest()(req).fold(
      formWithErrors => users.all().map { us =>
        BadRequest(htmls.users(us, UserFeedback.formed(formWithErrors)))
      },
      newUser => users.add(newUser).map { result =>
        val user = newUser.username
        val feedback = result.fold(
          _ => UserFeedback.error(s"User '$user' already exists."),
          _ => UserFeedback.success(s"Created user '$user'.")
        )
        Redirect(routes.Logs.allSources()).flashing(feedback.toSeq: _*)
      }
    )
  }

  def removeUser(user: Username) = async { _ =>
    users.remove(user) map { res =>
      val feedback = res.fold(
        _ => UserFeedback.error(s"User '$user' does not exist."),
        _ => UserFeedback.success(s"Deleted '$user'."))
      Redirect(routes.Logs.allSources()).flashing(feedback.toSeq: _*)
    }
  }

  def async(result: LogRequest => Future[Result]) = oauth.withAuthAsync(result)

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
