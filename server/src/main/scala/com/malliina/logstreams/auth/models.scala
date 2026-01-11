package com.malliina.logstreams.auth

import com.malliina.config.ConfigReadable
import com.malliina.http.SingleError
import com.malliina.values.{Email, Password, Username}
import io.circe.Codec

case class SecretKey(value: String) extends AnyVal:
  override def toString = "****"

object SecretKey:
  val dev = SecretKey("app-jwt-signing-secret-goes-here-must-be-sufficiently-long")
  given ConfigReadable[SecretKey] = ConfigReadable.string.map(apply)

case class BasicCredentials(username: Username, password: Password)

case class CookieConf(
  user: String,
  session: String,
  returnUri: String,
  lastId: String,
  provider: String,
  prompt: String
)

object CookieConf:
  def prefixed(prefix: String) = CookieConf(
    s"$prefix-user",
    s"$prefix-state",
    s"$prefix-return-uri",
    s"$prefix-last-id",
    s"$prefix-provider",
    s"$prefix-prompt"
  )

case class UserPayload(username: Username) derives Codec.AsObject

object UserPayload:
  def email(email: Email): UserPayload = apply(Username.unsafe(email.value))

enum AuthProvider(val name: String):
  case Google extends AuthProvider("google")

object AuthProvider:
  val PromptKey = "prompt"
  val SelectAccount = "select_account"

  private def forString(s: String): Either[SingleError, AuthProvider] =
    Seq(Google)
      .find(_.name == s)
      .toRight(SingleError(s"Unknown auth provider: '$s'."))

  def unapply(str: String): Option[AuthProvider] =
    forString(str).toOption
