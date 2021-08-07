package com.malliina.logstreams.auth

import cats.effect.IO
import com.malliina.logstreams.http4s.{Http4sAuth, IdentityError, JWTError, MissingCredentials}
import com.malliina.values.{Email, ErrorMessage, Password, Username}
import com.malliina.web.PermissionError
import org.http4s.Headers
import org.http4s.headers.Authorization

trait AuthBuilder {
  def apply(users: UserService[IO], web: Http4sAuth): Auther
}

trait Auther {
  def web: Http4sAuth
  def sources: Http4sAuthenticator[IO, Username]
  def viewers: Http4sAuthenticator[IO, Username]
}

class Auths(
  val sources: Http4sAuthenticator[IO, Username],
  val web: Http4sAuth
) extends Auther {
  val viewers = Auths.viewers(web)
}

object Auths extends AuthBuilder {
  val authorizedEmail = Email("malliina123@gmail.com")

  def apply(users: UserService[IO], web: Http4sAuth): Auther =
    new Auths(sources(users), web)

  def sources(users: UserService[IO]): Http4sAuthenticator[IO, Username] =
    new Http4sAuthenticator[IO, Username] {
      override def authenticate(hs: Headers): IO[Either[IdentityError, Username]] =
        basic(hs)
          .map { creds =>
            users
              .isValid(creds)
              .map { isValid =>
                if (isValid) Right(creds.username)
                else fail(hs)
              }
          }
          .fold(err => IO.pure(Left(err)), identity)
    }

  def viewers(auth: Http4sAuth): Http4sAuthenticator[IO, Username] =
    new Http4sAuthenticator[IO, Username] {
      override def authenticate(hs: Headers): IO[Either[IdentityError, Username]] =
        IO.pure(
          auth
            .authenticate(hs)
            .flatMap { u =>
              if (u.name == authorizedEmail.value) Right(u)
              else
                Left(JWTError(PermissionError(ErrorMessage(s"User '$u' is not authorized.")), hs))
            }
        )
    }

  private def basic[F[_]](hs: Headers) = hs
    .get[Authorization]
    .map { h =>
      h.credentials match {
        case org.http4s.BasicCredentials(user, pass) =>
          Right(com.malliina.logstreams.auth.BasicCredentials(Username(user), Password(pass)))
        case _ =>
          Left(MissingCredentials("Basic auth expected.", hs))
      }
    }
    .getOrElse { Left(MissingCredentials("No credentials.", hs)) }

  private def fail(headers: Headers) = Left(MissingCredentials("Invalid credentials.", headers))
}

trait Http4sAuthenticator[F[_], U] {
  def authenticate(req: Headers): F[Either[IdentityError, U]]
}
