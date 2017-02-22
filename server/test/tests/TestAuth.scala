package tests

import com.malliina.play.auth.InvalidCredentials
import com.malliina.play.http.{AuthedRequest, CookiedRequest, FullRequest}
import com.malliina.play.models.Username
import controllers.LogAuth
import play.api.mvc._

import scala.concurrent.Future

class TestAuth extends LogAuth {
  val testUser = Username("testuser")

  override def withAuthAsync(f: (CookiedRequest[AnyContent, AuthedRequest]) => Future[Result]) = Action.async { req =>
    val authReq = new AuthedRequest(testUser, req)
    val cookiedRequest = new CookiedRequest[AnyContent, AuthedRequest](authReq, req)
    f(cookiedRequest)
  }

  override def withAuth(f: FullRequest => Result): EssentialAction = Action { req =>
    val fakeRequest = new FullRequest(testUser, req, None)
    f(fakeRequest)
  }

  override def authenticateSocket(rh: RequestHeader): Future[Either[InvalidCredentials, Username]] =
    Future.successful(Right(testUser))
}
