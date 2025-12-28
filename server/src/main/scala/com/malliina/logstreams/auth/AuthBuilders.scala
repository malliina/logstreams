package com.malliina.logstreams.auth

import cats.Applicative
import cats.effect.kernel.Sync
import cats.syntax.all.toFunctorOps
import com.malliina.http4s.QueryParsers
import com.malliina.logstreams.http4s.{Http4sAuth, IdentityError, JWTError, MissingCredentials, SocketInfo}
import com.malliina.values.{Email, ErrorMessage, IdToken, Password, Username}
import com.malliina.web.PermissionError
import org.http4s.{Headers, ParseFailure, QueryParamDecoder, Request}
import org.http4s.headers.Authorization

trait AuthBuilder:
  def apply[F[_]: Sync](users: UserService[F], web: Http4sAuth[F]): Auther[F]

trait Auther[F[_]]:
  def web: Http4sAuth[F]
  def sources: HeaderAuthenticator[F, Username]
  def viewers: HeaderAuthenticator[F, Username]
  def public: RequestAuthenticator[F, SocketInfo]

class Auths[F[_]: Sync](
  val public: RequestAuthenticator[F, SocketInfo],
  val sources: HeaderAuthenticator[F, Username],
  val web: Http4sAuth[F]
) extends Auther[F]:
  val viewers = Auths.viewers(web)

object Auths extends AuthBuilder:
  private val authorizedEmail = Email("malliina123@gmail.com")

  val tokenQueryName = "token"

  given QueryParamDecoder[IdToken] = QueryParamDecoder.stringQueryParamDecoder.emap: str =>
    IdToken.build(str).left.map(err => ParseFailure(err.message, err.message))

  def apply[F[_]: Sync](users: UserService[F], web: Http4sAuth[F]): Auther[F] =
    new Auths(public(users, web), sources(users), web)

  def public[F[_]: Sync](
    users: UserService[F],
    web: Http4sAuth[F]
  ): RequestAuthenticator[F, SocketInfo] =
    (req: Request[F]) =>
      val F = Sync[F]
      val result: Either[IdentityError, SocketInfo] = for
        token <- QueryParsers
          .parse[IdToken](req.uri.query, tokenQueryName)
          .left
          .map(err => MissingCredentials(err.message.message, req.headers))
        verified <- web.jwt.verify[SocketInfo](token).left.map(err => JWTError(err, req.headers))
      yield verified
      result
        .map: socket =>
          users
            .exists(socket.app)
            .map: exists =>
              if exists then Right(socket)
              else fail(req.headers)
        .fold(err => F.pure(Left(err: IdentityError)), identity)

  def sources[F[_]: Sync](users: UserService[F]): HeaderAuthenticator[F, Username] =
    (hs: Headers) =>
      val F = Sync[F]
      basic(hs)
        .map: creds =>
          users
            .isValid(creds)
            .map: isValid =>
              if isValid then Right(creds.username)
              else fail(hs)
        .fold(
          err => F.pure(Left(err: IdentityError)),
          identity
        )

  def viewers[F[_]: Sync](auth: Http4sAuth[F]): HeaderAuthenticator[F, Username] =
    (hs: Headers) =>
      Applicative[F].pure(
        auth
          .authenticate(hs)
          .flatMap: u =>
            if u.name == authorizedEmail.value then Right(u)
            else Left(JWTError(PermissionError(ErrorMessage(s"User '$u' is not authorized.")), hs))
      )

  private def basic(hs: Headers) = hs
    .get[Authorization]
    .map: h =>
      h.credentials match
        case org.http4s.BasicCredentials(user, pass) =>
          Right(com.malliina.logstreams.auth.BasicCredentials(Username(user), Password(pass)))
        case _ =>
          Left(MissingCredentials("Basic auth expected.", hs))
    .getOrElse(Left(MissingCredentials("No credentials.", hs)))

  private def fail(headers: Headers): Left[IdentityError, Nothing] = Left(
    MissingCredentials("Invalid credentials.", headers)
  )

trait HeaderAuthenticator[F[_], U] extends RequestAuthenticator[F, U]:
  def authenticate(req: Headers): F[Either[IdentityError, U]]

  override def authenticate(req: Request[F]): F[Either[IdentityError, U]] =
    authenticate(req.headers)

trait RequestAuthenticator[F[_], U]:
  def authenticate(req: Request[F]): F[Either[IdentityError, U]]
