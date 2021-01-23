package tests

import cats.effect.IO
import com.malliina.logstreams.http4s.IdentityError
import com.malliina.values.Username
import controllers.{LogAuth, UserRequest}
import org.http4s.Request

class TestAuth extends LogAuth[IO] {
  val testUser = Username("testuser")

//  override def authAction(f: UserRequest => EssentialAction): EssentialAction =
//    EssentialAction { rh =>
//      f(UserRequest(testUser, rh)).apply(rh)
//    }
//
//  override def withAuthAsync(f: UserRequest => Future[Result]) =
//    actions.async { req =>
//      f(UserRequest(testUser, req))
//    }
//
//  override def withAuth(f: UserRequest => Result): EssentialAction =
//    actions { req =>
//      f(UserRequest(testUser, req))
//    }

  override def authenticateSocket(req: Request[IO]): IO[Either[IdentityError, UserRequest]] =
    IO.pure(Right(UserRequest(testUser, req.headers)))
}
