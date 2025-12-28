package com.malliina.logstreams.http4s

import com.malliina.logstreams.http4s.UserRequest.header
import com.malliina.logstreams.models.{AppName, LogClientId, UserAgent}
import com.malliina.values.{IdToken, Readable, Username}
import io.circe.Codec
import org.http4s.{Headers, Request}
import org.typelevel.ci.{CIString, CIStringSyntax}

import java.time.OffsetDateTime

case class UserFeedback(message: String, isError: Boolean) derives Codec.AsObject

object UserFeedback:
  val Feedback = "feedback"
  val Success = "success"
  val Yes = "yes"
  val No = "no"

  def success(message: String) = UserFeedback(message, isError = false)
  def error(message: String) = UserFeedback(message, isError = true)

case class UserRequest(
  user: Username,
  headers: Headers,
  address: String,
  clientId: Option[LogClientId],
  now: OffsetDateTime
):
  val userAgent = headers.header[UserAgent](ci"User-Agent")
  def describe: String = clientId.fold(user.name)(id => s"$user ($id)")

object UserRequest:
  def req(user: Username, req: Request[?]): UserRequest =
    make(user, req, req.headers.header[LogClientId](ci"X-Client-Id"))

  def make(user: Username, req: Request[?], clientId: Option[LogClientId]): UserRequest =
    UserRequest(
      user,
      req.headers,
      Urls.address(req),
      clientId,
      OffsetDateTime.now()
    )

  extension (hs: Headers)
    def header[T](name: CIString)(using r: Readable[T]): Option[T] =
      hs.get(name).map(_.head.value).flatMap(str => r.read(str).toOption)

case class TokenRequest(app: AppName) derives Codec.AsObject

case class SocketInfo(app: AppName, clientId: LogClientId) derives Codec.AsObject:
  def describe = s"$app ($clientId)"

object SocketInfo:
  def make(app: AppName) = SocketInfo(app, LogClientId.random())

case class TokenResponse(token: IdToken) derives Codec.AsObject
