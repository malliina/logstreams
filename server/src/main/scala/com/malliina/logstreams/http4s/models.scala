package com.malliina.logstreams.http4s

import com.malliina.logstreams.db.Admin
import com.malliina.logstreams.http4s.UserRequest.header
import com.malliina.logstreams.models.{AppName, Lang, Language, LogClientId, UserAgent}
import com.malliina.values.{Email, IdToken, Readable, Username}
import io.circe.Codec
import org.http4s.{Headers, Request}
import org.typelevel.ci.{CIString, CIStringSyntax}

import java.time.{Instant, OffsetDateTime}

case class UserFeedback(message: String, isError: Boolean) derives Codec.AsObject

object UserFeedback:
  val Feedback = "feedback"
  val Success = "success"
  val Yes = "yes"
  val No = "no"

  def success(message: String) = UserFeedback(message, isError = false)
  def error(message: String) = UserFeedback(message, isError = true)

case class AdminUser(email: Email, language: Language, now: OffsetDateTime):
  def lang = Lang(language)

object AdminUser:
  def make(admin: Admin): AdminUser = AdminUser(admin.email, admin.language, OffsetDateTime.now())

case class UserRequest(
  user: Username,
  headers: Headers,
  address: String,
  language: Language,
  clientId: Option[LogClientId],
  now: OffsetDateTime
):
  val userAgent = headers.header[UserAgent](ci"User-Agent")
  def describe: String = clientId.fold(user.name)(id => s"$user ($id)")
  def lang = Lang(language)

object UserRequest:
  def req(user: Username, req: Request[?]): UserRequest =
    make(user, Language.default, req, req.headers.header[LogClientId](ci"X-Client-Id"))

  def make(
    user: Username,
    language: Language,
    req: Request[?],
    clientId: Option[LogClientId]
  ): UserRequest =
    UserRequest(
      user,
      req.headers,
      Urls.address(req),
      language,
      clientId,
      OffsetDateTime.now()
    )

  extension (hs: Headers)
    def header[T](name: CIString)(using r: Readable[T]): Option[T] =
      hs.get(name).map(_.head.value).flatMap(str => r.read(str).toOption)

case class JsonRequest[T](user: AdminUser, payload: T)

case class ChangeLanguage(language: Language) derives Codec.AsObject

case class TokenRequest(app: AppName) derives Codec.AsObject

case class SourceInfo(app: AppName, clientId: LogClientId) derives Codec.AsObject:
  def describe = s"$app ($clientId)"

object SourceInfo:
  def make(app: AppName) = SourceInfo(app, LogClientId.random())

case class TokenResponse(token: IdToken) derives Codec.AsObject

case class Published(eventCount: Int) derives Codec.AsObject
