package controllers

import akka.stream.Materializer
import com.malliina.play.controllers.{OAuthControl, OAuthSecured}
import com.malliina.play.http.FullRequest
import com.malliina.play.models.Username
import play.api.mvc._

import scala.concurrent.Future

trait OAuthRoutes {
  def initiate: EssentialAction

  def redirResponse: EssentialAction
}

trait LogAuth {
  def withAuth(f: FullRequest => Result): EssentialAction

  def authenticateSocket(rh: RequestHeader): Future[Option[Username]]
}

class WebAuth(oauth: OAuthCtrl) extends LogAuth {
  override def withAuth(f: FullRequest => Result) = oauth.authAction(f)

  override def authenticateSocket(rh: RequestHeader) =
    oauth.authenticateFromSession(rh)
}

class WebOAuthRoutes(oauth: OAuthCtrl) extends OAuthRoutes {
  override def initiate = oauth.initiate

  override def redirResponse = oauth.redirResponse
}

class OAuthCtrl(oauth: OAuth) extends OAuthSecured(oauth, oauth.mat) {
  def initiate = oauth.initiate

  def redirResponse = oauth.redirResponse
}

class OAuth(val mat: Materializer) extends OAuthControl(mat) {
  override val sessionUserKey: String = "email"

  override def isAuthorized(email: String) = email == "malliina123@gmail.com"

  override def startOAuth = routes.OAuthRoutes.initiate()

  override def oAuthRedir = routes.OAuthRoutes.redirResponse()

  override def ejectCall = routes.Logs.index()

  override def onOAuthSuccess = routes.Logs.index()
}
