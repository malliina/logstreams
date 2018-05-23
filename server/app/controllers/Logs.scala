package controllers

import com.malliina.app.AppMeta
import com.malliina.logstreams.auth.UserService
import com.malliina.logstreams.html.Htmls
import com.malliina.logstreams.models.AppName
import com.malliina.play.ActorExecution
import com.malliina.play.auth._
import com.malliina.play.controllers.Caching
import com.malliina.play.http.{AuthedRequest, CookiedRequest}
import com.malliina.play.models.{Password, Username}
import controllers.Assets.Asset
import controllers.Logs.{PasswordKey, UsernameKey, log}
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms.mapping
import play.api.http.Writeable
import play.api.libs.json.Json
import play.api.mvc._
import play.filters.csrf.CSRF

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
           assets: AssetsBuilder,
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

  // Assets

  def versioned(path: String, file: Asset) = assets.versioned(path, file)

  // HTML

  def index = async { _ => users.all().map { us => Ok(htmls.logs(us.map(u => AppName(u.name)))) } }

  def sources = navigate(htmls.sources)

  def allUsers = async { req =>
    CSRF.getToken(req.rh).map { csrfToken =>
      users.all() map { us =>
        Ok(htmls.users(us, csrfToken, UserFeedback.flashed(req.rh)))
      }
    }.getOrElse {
      Future.successful(failCSRF)
    }
  }

  def addUser = oauth.authAction { req =>
    Action.async { request =>
      addUserForm.bindFromRequest()(request).fold(
        formWithErrors => users.all().map { us =>
          CSRF.getToken(request).map { csrfToken =>
            BadRequest(htmls.users(us, csrfToken, UserFeedback.formed(formWithErrors)))
          }.getOrElse {
            failCSRF
          }
        },
        newUser => users.add(newUser).map { result =>
          val user = newUser.username
          val feedback = result.fold(
            _ => {
              log error buildMessage(req, s"failed to add '$user' because that user already exists.")
              UserFeedback.error(s"User '$user' already exists.")
            },
            _ => {
              log info buildMessage(req, s"created '$user'")
              UserFeedback.success(s"Created user '$user'.")
            }
          )
          redirectWith(feedback)
        }
      )
    }
  }

  def failCSRF = BadRequest("CSRF error.")

  def removeUser(user: Username) = async { req =>
    users.remove(user) map { res =>
      val feedback = res.fold(
        _ => {
          log error buildMessage(req, s"failed to delete '$user' because that user does not exist")
          UserFeedback.error(s"User '$user' does not exist.")
        },
        _ => {
          log info buildMessage(req, s"deleted '$user'.")
          UserFeedback.success(s"Deleted '$user'.")
        })
      redirectWith(feedback)
    }
  }

  def buildMessage(req: UserRequest, message: String) = s"User '${req.user}' from '${req.address}' $message."

  private def redirectWith(feedback: UserFeedback) =
    Redirect(routes.Logs.allUsers()).flashing(feedback.flash)

  def async(result: UserRequest => Future[Result]) =
    oauth.withAuthAsync(result)

  def navigate[C: Writeable](content: => C): EssentialAction =
    oauth.withAuth(_ => Ok(content))
}
