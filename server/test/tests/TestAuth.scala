package tests

import com.malliina.play.auth.InvalidCredentials
import com.malliina.values.Username
import controllers.{LogAuth, UserRequest}
import play.api.mvc.{ActionBuilder, _}

import scala.concurrent.Future

class TestAuth(actions: ActionBuilder[Request, AnyContent]) extends LogAuth {
  val testUser = Username("testuser")

  override def authAction(f: UserRequest => EssentialAction): EssentialAction =
    EssentialAction { rh =>
      f(UserRequest(testUser, rh)).apply(rh)
    }

  override def withAuthAsync(f: UserRequest => Future[Result]) =
    actions.async { req =>
      f(UserRequest(testUser, req))
    }

  override def withAuth(f: UserRequest => Result): EssentialAction =
    actions { req =>
      f(UserRequest(testUser, req))
    }

  override def authenticateSocket(
    rh: RequestHeader
  ): Future[Either[InvalidCredentials, UserRequest]] =
    Future.successful(Right(UserRequest(testUser, rh)))
}
