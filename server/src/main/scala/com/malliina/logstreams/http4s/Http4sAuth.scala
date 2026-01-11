package com.malliina.logstreams.http4s

import com.malliina.logstreams.auth.{AuthProvider, CookieConf, JWT, UserPayload}
import com.malliina.values.{IdToken, Username}
import io.circe.{Decoder, Encoder}
import org.http4s.Credentials.Token
import org.http4s.headers.{Authorization, Cookie}
import org.http4s.{Headers, HttpDate, Request, Response, ResponseCookie}

import scala.concurrent.duration.DurationInt

class Http4sAuth[F[_]](
  val jwt: JWT,
  val cookieNames: CookieConf = CookieConf.prefixed("logstreams")
):
  private val cookiePath = Option("/")

  def authenticate(headers: Headers): Either[IdentityError, Username] =
    readUser(cookieNames.user, headers)

  def session[T: Decoder](from: Headers): Either[IdentityError, T] =
    read[T](cookieNames.session, from)

  def token(headers: Headers) = headers
    .get[Authorization]
    .toRight(MissingCredentials("Missing Authorization header", headers))
    .flatMap(_.credentials match
      case Token(_, token) =>
        IdToken.build(token).left.map(err => MissingCredentials(err.message, headers))
      case _ => Left(MissingCredentials("Missing token.", headers)))

  def withSession[T: Encoder](t: T, req: Request[F], res: Response[F]): res.Self =
    withJwt(cookieNames.session, t, req, res)

  def clearSession(res: Response[F]): res.Self =
    res
      .removeCookie(cookieNames.provider)
      .removeCookie(ResponseCookie(cookieNames.session, "", path = cookiePath))
      .removeCookie(ResponseCookie(cookieNames.user, "", path = cookiePath))

  def withAppUser(
    user: UserPayload,
    provider: AuthProvider,
    req: Request[F],
    res: Response[F]
  ): Response[F] = withUser(user, req, res)
    .removeCookie(cookieNames.returnUri)
    .removeCookie(cookieNames.prompt)
    .addCookie(responseCookie(cookieNames.lastId, user.username.name))
    .addCookie(responseCookie(cookieNames.provider, provider.name))

  private def withUser[T: Encoder](t: T, req: Request[F], res: Response[F]): res.Self =
    withJwt(cookieNames.user, t, req, res)

  private def withJwt[T: Encoder](
    cookieName: String,
    t: T,
    req: Request[F],
    res: Response[F]
  ): res.Self =
    val signed = jwt.sign[T](t, 12.hours)
    val top = Urls.topDomainFrom(req)
    res.addCookie(
      ResponseCookie(
        cookieName,
        signed.value,
        httpOnly = true,
        secure = Urls.isSecure(req),
        path = cookiePath,
        domain = Option.when(top.nonEmpty)(top)
      )
    )

  private def responseCookie(name: String, value: String) = ResponseCookie(
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
    for
      header <- headers.get[Cookie].toRight(MissingCredentials("Cookie parsing error.", headers))
      cookie <-
        header.values
          .find(_.name == cookieName)
          .toRight(MissingCredentials(s"Cookie not found: '$cookieName'.", headers))
          .flatMap(c =>
            IdToken.build(c.content).left.map(err => MissingCredentials(err.message, headers))
          )
      t <- jwt
        .verify[T](cookie)
        .left
        .map: err =>
          JWTError(err, headers)
    yield t
