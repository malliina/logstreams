package controllers

import akka.stream.Materializer
import com.malliina.play.controllers.{OAuthControl, OAuthSecured}
import play.api.http.Writeable
import play.api.mvc.Results.Ok
import play.api.mvc.{EssentialAction, RequestHeader}

class OAuthCtrl(oauth: OAuth) extends OAuthSecured(oauth, oauth.mat) {
  def initiate = oauth.initiate

  def redirResponse = oauth.redirResponse

  def navigate[C: Writeable](f: RequestHeader => C): EssentialAction =
    authAction(req => Ok(f(req)))
}

class OAuth(val mat: Materializer) extends OAuthControl(mat) {
  override val sessionUserKey: String = "email"

  override def isAuthorized(email: String) = email == "malliina123@gmail.com"

  override def startOAuth = routes.OAuthCtrl.initiate()

  override def oAuthRedir = routes.OAuthCtrl.redirResponse()

  override def ejectCall = routes.Logs.index()

  override def onOAuthSuccess = routes.Logs.index()
}
