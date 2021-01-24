package controllers

import cats.effect.IO
import com.malliina.http.OkClient
import com.malliina.logstreams.http4s.{IdentityError, LogRoutes}
import com.malliina.util.AppLogger
import com.malliina.values.{Email, Username}
import com.malliina.web.AuthConf
import org.http4s.{Headers, Request}

import scala.concurrent.duration.{Duration, DurationInt}

trait LogAuth[F[_]] {
  def authenticateSocket(req: Request[IO]): F[Either[IdentityError, UserRequest]]
}

case class UserRequest(user: Username, headers: Headers) {
  def address = "todo"
}

object OAuth {
  private val log = AppLogger(getClass)

//  def sessionAuthenticator(oauth: OAuth): Authenticator[UserRequest] = {
//    Authenticator { rh =>
//      val result = Auth
//        .authenticateFromSession(rh, oauth.sessionUserKey)
//        .map(user => UserRequest(user, rh))
//        .toRight(MissingCredentials(rh))
//      Future.successful(result)
//    }
//  }

//  def authBundle(oauth: OAuth): AuthBundle[UserRequest] = new AuthBundle[UserRequest] {
//    override val authenticator = sessionAuthenticator(oauth)
//
//    override def onUnauthorized(failure: AuthFailure) =
//      Results.Redirect(routes.OAuth.googleStart())
//  }
}

class OAuth(creds: AuthConf) {
  val LoginCookieDuration: Duration = 3650.days
  val log = OAuth.log
  val http = OkClient.default
  val authorizedEmail = Email("malliina123@gmail.com")
  val reverse = LogRoutes
//  val handler = BasicAuthHandler(
//    reverse.index,
//    sessionKey = "logsEmail",
//    lastIdKey = "logsLastId",
//    authorize = email =>
//      if (email == authorizedEmail) Right(email)
//      else Left(PermissionError(ErrorMessage(s"Unauthorized: '$email'.")))
//  )
//  val sessionUserKey: String = handler.sessionKey
//
//  val conf = AuthConf(creds.clientId, creds.clientSecret)
//  val validator = GoogleCodeValidator(OAuthConf(routes.OAuth.googleCallback(), handler, conf, http))

//  def googleStart = actions.async { req =>
//    val lastId = req.cookies.get(handler.lastIdKey).map(_.value)
//    val described = lastId.fold("without login hint")(h => s"with login hint '$h'")
//    log.info(s"Starting OAuth flow $described.")
//    validator.startHinted(req, lastId)
//  }
//
//  def googleCallback = actions.async { req =>
//    val describe =
//      req.session.get(OAuthKeys.State).fold("without state")(s => s"with a state of '$s'")
//    log.info(s"Validating OAuth callback $describe...")
//    validator.validateCallback(req)
//  }
}
