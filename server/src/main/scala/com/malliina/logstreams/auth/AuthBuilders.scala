package com.malliina.logstreams.auth

import cats.effect.IO
import com.malliina.logstreams.http4s.{Http4sAuth, IdentityError, MissingCredentials}
import com.malliina.play.auth.BasicCredentials
import com.malliina.values.{Password, Username}
import org.http4s.Headers
import org.http4s.headers.Authorization

class Auths(
  val sources: Http4sAuthenticator[IO, Username],
  val web: Http4sAuth
) {
  val viewers = Auths.viewers(web)
}

object Auths {
  def apply(users: UserService[IO], web: Http4sAuth) =
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
        IO.pure(auth.authenticate(hs))
    }

  private def basic[F[_]](hs: Headers) = hs
    .get(Authorization)
    .map { h =>
      h.credentials match {
        case org.http4s.BasicCredentials(user, pass) =>
          Right(BasicCredentials(Username(user), Password(pass)))
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
