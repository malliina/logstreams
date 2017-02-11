package controllers

import akka.actor.{ActorSystem, Props}
import akka.stream.Materializer
import com.malliina.app.AppMeta
import com.malliina.logstreams._
import com.malliina.logstreams.auth.UserService
import com.malliina.logstreams.tags.Htmls
import com.malliina.play.auth.{Auth, BasicCredentials}
import com.malliina.play.controllers.BaseController
import com.malliina.play.http.{AuthedRequest, CookiedRequest}
import com.malliina.play.models.{Password, Username}
import controllers.Logs.{LogRequest, PasswordKey, UsernameKey}
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms.mapping
import play.api.http.Writeable
import play.api.libs.json.{JsValue, Json}
import play.api.libs.streams.ActorFlow
import play.api.mvc._

import scala.concurrent.Future

object Logs {
  private val log = Logger(getClass)

  type LogRequest = CookiedRequest[AnyContent, AuthedRequest]

  val UsernameKey = "username"
  val PasswordKey = "password"
}

class Logs(htmls: Htmls,
           oauth: LogAuth,
           users: UserService,
           actorSystem: ActorSystem,
           mat: Materializer)
  extends Controller {

  implicit val as = actorSystem
  implicit val m = mat
  implicit val ec = mat.executionContext

  val addUserForm = Form(mapping(
    UsernameKey -> Username.mapping,
    PasswordKey -> Password.mapping
  )(BasicCredentials.apply)(BasicCredentials.unapply))

  val mediator = actorSystem.actorOf(MediatorActor.props())

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

  def listenerSocket = viewerSocket(LogViewerActor.props)

  def adminSocket = viewerSocket(SourceViewerActor.props)

  def sourceSocket = logStreamsSocket(authenticateSource, SourceActor.props)

  private def authenticateSource(req: RequestHeader) =
    Auth.basicCredentials(req)
      .map(creds => users.isValid(creds).map(isValid => if (isValid) Option(creds.username) else None))
      .getOrElse(Future.successful(None))

  private def viewerSocket(buildFlow: Listener => Props) =
    logStreamsSocket(oauth.authenticateSocket, buildFlow)

  private def logStreamsSocket(auth: RequestHeader => Future[Option[Username]], flow: Listener => Props) =
    WebSocket.acceptOrResult[Any, JsValue] { req =>
      auth(req) map { maybeUser =>
        maybeUser
          .map(user => Right(ActorFlow.actorRef(out => flow(Listener(out, req, user, mediator)))))
          .getOrElse(Left(Unauthorized))
      }
    }

  // Utils

  def ping = Action(BaseController.NoCacheOk(Json.toJson(AppMeta.ThisApp)))
}
