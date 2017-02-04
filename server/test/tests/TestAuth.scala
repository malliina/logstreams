package tests

import com.malliina.play.http.FullRequest
import com.malliina.play.models.Username
import controllers.LogAuth
import play.api.mvc.{Action, EssentialAction, RequestHeader, Result}

import scala.concurrent.Future

class TestAuth extends LogAuth {
  val testUser = Username("testuser")

  override def withAuth(f: FullRequest => Result): EssentialAction = Action { req =>
    val fakeRequest = new FullRequest(Username("testuser"), req, None)
    f(fakeRequest)
  }

  override def authenticateSocket(rh: RequestHeader): Future[Option[Username]] =
    Future.successful(Option(testUser))
}
