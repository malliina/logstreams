package controllers

import com.malliina.app.AppMeta
import com.malliina.logstreams.auth.UserService
import com.malliina.logstreams.tags.Htmls
import com.malliina.play.ActorExecution
import com.malliina.play.auth._
import com.malliina.play.controllers.Caching
import com.malliina.play.http.{AuthedRequest, CookiedRequest}
import com.malliina.play.models.{Password, Username}
import controllers.Logs.{LogRequest, PasswordKey, UsernameKey}
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms.mapping
import play.api.http.Writeable
import play.api.libs.json.Json
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
           dep: ActorExecution,
           comps: ControllerComponents)
  extends AbstractController(comps) {

  implicit val as = dep.actorSystem
  implicit val m = dep.materializer
  implicit val ec = dep.executionContext

  val addUserForm = Form(mapping(
    UsernameKey -> Username.mapping,
    PasswordKey -> Password.mapping
  )(BasicCredentials.apply)(BasicCredentials.unapply))

  def ping = Action(Caching.NoCacheOk(Json.toJson(AppMeta.ThisApp)))

  // HTML

  def index = navigate(htmls.logs)

  def sources = navigate(htmls.sources)

  def allSources = async { req =>
    users.all() map { us =>
      Ok(htmls.users(us, UserFeedback.flashed(req.rh)))
    }
  }

  def action = Action(parse.json) { req => Ok }

  def addUser = oauth.authAction { _ =>
    Action.async { req =>
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
  }

  def removeUser(user: Username) = async { _ =>
    users.remove(user) map { res =>
      val feedback = res.fold(
        _ => UserFeedback.error(s"User '$user' does not exist."),
        _ => UserFeedback.success(s"Deleted '$user'."))
      Redirect(routes.Logs.allSources()).flashing(feedback.toSeq: _*)
    }
  }

  def async(result: UserRequest => Future[Result]) =
    oauth.withAuthAsync(result)

  def navigate[C: Writeable](content: => C): EssentialAction =
    oauth.withAuth(_ => Ok(content))
}
