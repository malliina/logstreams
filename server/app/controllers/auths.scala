package controllers

import akka.stream.Materializer
import com.malliina.oauth.GoogleOAuthCredentials
import com.malliina.play.auth.InvalidCredentials
import com.malliina.play.controllers.{OAuthControl, OAuthSecured}
import com.malliina.play.http.{AuthedRequest, CookiedRequest, FullRequest}
import com.malliina.play.models.Username
import play.api.mvc._

import scala.concurrent.Future

trait LogAuth {
  def withAuthAsync(f: CookiedRequest[AnyContent, AuthedRequest] => Future[Result]): EssentialAction

  def withAuth(f: FullRequest => Result): EssentialAction

  def authenticateSocket(rh: RequestHeader): Future[Either[InvalidCredentials, Username]]
}

class WebAuth(oauth: OAuthCtrl) extends LogAuth {
  implicit val ec = oauth.ec

  override def withAuthAsync(f: (CookiedRequest[AnyContent, AuthedRequest]) => Future[Result]) =
    oauth.authActionAsync(f)

  override def withAuth(f: FullRequest => Result) =
    oauth.authAction(f)

  override def authenticateSocket(rh: RequestHeader) =
    oauth.authenticateFromSession(rh).map(_.toRight(InvalidCredentials(rh)))
}

class OAuthCtrl(oauth: OAuth) extends OAuthSecured(oauth, oauth.mat)

class OAuth(creds: GoogleOAuthCredentials, val mat: Materializer) extends OAuthControl(creds, mat) {
  override val sessionUserKey: String = "email"

  override def isAuthorized(email: String) = email == "malliina123@gmail.com"

  override def startOAuth = routes.OAuth.initiate()

  override def oAuthRedir = routes.OAuth.redirResponse()

  override def ejectCall = routes.Logs.index()

  override def onOAuthSuccess = routes.Logs.index()
}
