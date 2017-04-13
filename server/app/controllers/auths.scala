package controllers

import akka.stream.Materializer
import com.malliina.oauth.GoogleOAuthCredentials
import com.malliina.play.auth._
import com.malliina.play.controllers.{AuthBundle, BaseSecurity, OAuthControl}
import com.malliina.play.models.Username
import play.api.mvc._

import scala.concurrent.Future

trait LogAuth {
  def authAction(f: UserRequest => EssentialAction): EssentialAction

  def withAuthAsync(f: UserRequest => Future[Result]): EssentialAction

  def withAuth(f: UserRequest => Result): EssentialAction

  def authenticateSocket(rh: RequestHeader): Future[Either[AuthFailure, UserRequest]]
}

class WebAuth(oauth: OAuthCtrl) extends LogAuth {
  implicit val ec = oauth.ec

  override def authAction(f: UserRequest => EssentialAction): EssentialAction =
    oauth.authenticatedLogged(f)

  override def withAuthAsync(f: UserRequest => Future[Result]) =
    oauth.authActionAsync(f)

  override def withAuth(f: UserRequest => Result): EssentialAction =
    oauth.authAction(f)

  override def authenticateSocket(rh: RequestHeader) =
    oauth.authenticate(rh)
}

class OAuthCtrl(oauth: OAuth) extends BaseSecurity(OAuth.authBundle(oauth), oauth.mat)

case class UserRequest(user: Username, rh: RequestHeader)

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
      Results.Redirect(oauth.startOAuth)
  }
}

class OAuth(creds: GoogleOAuthCredentials, mat: Materializer)
  extends OAuthControl(creds, mat) {
  override val sessionUserKey: String = "email"

  override def isAuthorized(email: String) = email == "malliina123@gmail.com"

  override def startOAuth = routes.OAuth.initiate()

  override def oAuthRedir = routes.OAuth.redirResponse()

  override def ejectCall = routes.Logs.index()

  override def onOAuthSuccess = routes.Logs.index()
}
