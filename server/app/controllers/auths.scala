package controllers

import akka.stream.Materializer
import com.malliina.http.OkClient
import com.malliina.oauth.GoogleOAuthCredentials
import com.malliina.play.auth._
import com.malliina.play.controllers.{AuthBundle, BaseSecurity}
import com.malliina.play.http.Proxies
import com.malliina.play.models.{AuthInfo, Email, Username}
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

trait LogAuth {
  def authAction(f: UserRequest => EssentialAction): EssentialAction

  def withAuthAsync(f: UserRequest => Future[Result]): EssentialAction

  def withAuth(f: UserRequest => Result): EssentialAction

  def authenticateSocket(rh: RequestHeader): Future[Either[AuthFailure, UserRequest]]
}

class WebAuth(oauth: OAuthCtrl) extends LogAuth {
  implicit val ec: ExecutionContext = oauth.ec

  override def authAction(f: UserRequest => EssentialAction): EssentialAction =
    oauth.authenticatedLogged(f)

  override def withAuthAsync(f: UserRequest => Future[Result]) =
    oauth.authActionAsync(f)

  override def withAuth(f: UserRequest => Result): EssentialAction =
    oauth.authAction(f)

  override def authenticateSocket(rh: RequestHeader) =
    oauth.authenticate(rh)
}

class OAuthCtrl(oauth: OAuth, mat: Materializer)
  extends BaseSecurity(oauth.actions, OAuth.authBundle(oauth), mat)

case class UserRequest(user: Username, rh: RequestHeader) extends AuthInfo {
  def address = Proxies.realAddress(rh)
}

object OAuth {
  def sessionAuthenticator(oauth: OAuth): Authenticator[UserRequest] = {
    Authenticator { rh =>
      val result = Auth.authenticateFromSession(rh, oauth.sessionUserKey)
        .map(user => UserRequest(user, rh))
        .toRight(MissingCredentials(rh))
      Future.successful(result)
    }
  }

  def authBundle(oauth: OAuth) = new AuthBundle[UserRequest] {
    override val authenticator = sessionAuthenticator(oauth)

    override def onUnauthorized(failure: AuthFailure) =
      Results.Redirect(routes.OAuth.googleStart())
  }
}

class OAuth(val actions: ActionBuilder[Request, AnyContent], creds: GoogleOAuthCredentials) {
  val http = OkClient.default
  val authorizedEmail = Email("malliina123@gmail.com")
  val sessionUserKey: String = "email"
  val handler = new BasicAuthHandler(routes.Logs.index(), sessionKey = sessionUserKey).filter(_ == authorizedEmail)
  val conf = AuthConf(creds.clientId, creds.clientSecret)
  val validator = StandardCodeValidator(CodeValidationConf.google(routes.OAuth.googleCallback(), handler, conf, http))

  def googleStart = actions.async(req => validator.start(req))

  def googleCallback = actions.async(req => validator.validateCallback(req))
}
