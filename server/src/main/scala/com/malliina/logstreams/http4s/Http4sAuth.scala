package com.malliina.logstreams.http4s

import cats.effect.IO
import com.malliina.logstreams.auth.{AuthProvider, CookieConf, JWT, UserPayload}
import com.malliina.values.{IdToken, Username}
import io.circe.{Decoder, Encoder}
import org.http4s.Credentials.Token
import org.http4s.headers.{Authorization, Cookie}
import org.http4s.{Headers, HttpDate, Response, ResponseCookie}

import scala.concurrent.duration.DurationInt

object Http4sAuth {
  def apply(jwt: JWT): Http4sAuth = new Http4sAuth(jwt)
}

class Http4sAuth(
  val jwt: JWT,
  val cookieNames: CookieConf = CookieConf.prefixed("logstreams")
) {
  val cookiePath = Option("/")

  def authenticate(headers: Headers): Either[IdentityError, Username] =
    readUser(cookieNames.user, headers)

  def session[T: Decoder](from: Headers): Either[IdentityError, T] =
    read[T](cookieNames.session, from)

  def token(headers: Headers) = headers
    .get[Authorization]
    .toRight(MissingCredentials("Missing Authorization header", headers))
    .flatMap(_.credentials match {
      case Token(_, token) => Right(IdToken(token))
      case _               => Left(MissingCredentials("Missing token.", headers))
    })

  def withSession[T: Encoder](t: T, isSecure: Boolean, res: Response[IO]): res.Self =
    withJwt(cookieNames.session, t, isSecure, res)

  def clearSession(res: Response[IO]): res.Self =
    res
      .removeCookie(cookieNames.provider)
      .removeCookie(ResponseCookie(cookieNames.session, "", path = cookiePath))
      .removeCookie(ResponseCookie(cookieNames.user, "", path = cookiePath))

  def withAppUser(
    user: UserPayload,
    isSecure: Boolean,
    provider: AuthProvider,
    res: Response[IO]
  ) = withUser(user, isSecure, res)
    .removeCookie(cookieNames.returnUri)
    .addCookie(responseCookie(cookieNames.lastId, user.username.name))
    .addCookie(responseCookie(cookieNames.provider, provider.name))

  def withUser[T: Encoder](t: T, isSecure: Boolean, res: Response[IO]): res.Self =
    withJwt(cookieNames.user, t, isSecure, res)

  def withJwt[T: Encoder](
    cookieName: String,
    t: T,
    isSecure: Boolean,
    res: Response[IO]
  ): res.Self = {
    val signed = jwt.sign[T](t, 12.hours)
    res.addCookie(
      ResponseCookie(
        cookieName,
        signed.value,
        httpOnly = true,
        secure = isSecure,
        path = cookiePath
      )
    )
  }

  def responseCookie(name: String, value: String) = ResponseCookie(
    name,
    value,
    Option(HttpDate.MaxValue),
    path = cookiePath,
    secure = true,
    httpOnly = true
  )

  private def readUser(cookieName: String, headers: Headers): Either[IdentityError, Username] =
    read[UserPayload](cookieName, headers).map(_.username)

  private def read[T: Decoder](cookieName: String, headers: Headers): Either[IdentityError, T] =
    for {
      header <- headers.get[Cookie].toRight(MissingCredentials("Cookie parsing error.", headers))
      cookie <-
        header.values
          .find(_.name == cookieName)
          .map(c => IdToken(c.content))
          .toRight(MissingCredentials(s"Cookie not found: '$cookieName'.", headers))
      t <- jwt.verify[T](cookie).left.map { err =>
        JWTError(err, headers)
      }
    } yield t
}
