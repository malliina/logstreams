package com.malliina.logstreams.auth

import cats.Applicative
import cats.effect.kernel.Sync
import cats.syntax.all.toFunctorOps
import com.malliina.logstreams.http4s.{Http4sAuth, IdentityError, JWTError, MissingCredentials}
import com.malliina.values.{Email, ErrorMessage, Password, Username}
import com.malliina.web.PermissionError
import org.http4s.Headers
import org.http4s.headers.Authorization

trait AuthBuilder:
  def apply[F[_]: Sync](users: UserService[F], web: Http4sAuth[F]): Auther[F]

trait Auther[F[_]]:
  def web: Http4sAuth[F]
  def sources: Http4sAuthenticator[F, Username]
  def viewers: Http4sAuthenticator[F, Username]

class Auths[F[_]: Sync](
  val sources: Http4sAuthenticator[F, Username],
  val web: Http4sAuth[F]
) extends Auther[F]:
  val viewers = Auths.viewers(web)

object Auths extends AuthBuilder:
  private val authorizedEmail = Email("malliina123@gmail.com")

  def apply[F[_]: Sync](users: UserService[F], web: Http4sAuth[F]): Auther[F] =
    new Auths(sources(users), web)

  def sources[F[_]: Sync](users: UserService[F]): Http4sAuthenticator[F, Username] =
    (hs: Headers) =>
      val F = Sync[F]
      basic(hs).map { creds =>
        users
          .isValid(creds)
          .map { isValid =>
            if isValid then Right(creds.username)
            else fail(hs)
          }
      }
        .fold(
          err => F.pure(Left(err: IdentityError)),
          identity
        )

  def viewers[F[_]: Sync](auth: Http4sAuth[F]): Http4sAuthenticator[F, Username] =
    (hs: Headers) =>
      Applicative[F].pure(
        auth
          .authenticate(hs)
          .flatMap { u =>
            if u.name == authorizedEmail.value then Right(u)
            else Left(JWTError(PermissionError(ErrorMessage(s"User '$u' is not authorized.")), hs))
          }
      )

  private def basic(hs: Headers) = hs
    .get[Authorization]
    .map { h =>
      h.credentials match
        case org.http4s.BasicCredentials(user, pass) =>
          Right(com.malliina.logstreams.auth.BasicCredentials(Username(user), Password(pass)))
        case _ =>
          Left(MissingCredentials("Basic auth expected.", hs))
    }
    .getOrElse { Left(MissingCredentials("No credentials.", hs)) }

  private def fail(headers: Headers): Left[IdentityError, Nothing] = Left(
    MissingCredentials("Invalid credentials.", headers)
  )

trait Http4sAuthenticator[F[_], U]:
  def authenticate(req: Headers): F[Either[IdentityError, U]]
