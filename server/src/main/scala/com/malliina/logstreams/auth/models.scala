package com.malliina.logstreams.auth

import com.malliina.logstreams.SingleError
import com.malliina.values.{Email, Username}
import play.api.libs.json.Json

case class SecretKey(value: String) extends AnyVal {
  override def toString = "****"
}

case class CookieConf(
  user: String,
  session: String,
  returnUri: String,
  lastId: String,
  provider: String,
  prompt: String
)

object CookieConf {
  def prefixed(prefix: String) = CookieConf(
    s"$prefix-user",
    s"$prefix-state",
    s"$prefix-return-uri",
    s"$prefix-last-id",
    s"$prefix-provider",
    s"$prefix-prompt"
  )
}

case class UserPayload(username: Username)

object UserPayload {
  implicit val json = Json.format[UserPayload]

  def email(email: Email): UserPayload = apply(Username(email.value))
}

sealed abstract class AuthProvider(val name: String)

object AuthProvider {
  def forString(s: String): Either[SingleError, AuthProvider] =
    Seq(Google)
      .find(_.name == s)
      .toRight(SingleError(s"Unknown auth provider: '$s'."))

  def unapply(str: String): Option[AuthProvider] =
    forString(str).toOption

  case object Google extends AuthProvider("google")
}
