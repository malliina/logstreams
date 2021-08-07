package com.malliina.logstreams.auth

import com.malliina.logstreams.{ConfigReadable, SingleError}
import com.malliina.values.{Email, Password, Username}
import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._

case class SecretKey(value: String) extends AnyVal {
  override def toString = "****"
}

object SecretKey {
  implicit val config: ConfigReadable[SecretKey] = ConfigReadable.string.map(apply)
}

case class BasicCredentials(username: Username, password: Password)

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
  implicit val json: Codec[UserPayload] = deriveCodec[UserPayload]

  def email(email: Email): UserPayload = apply(Username(email.value))
}

sealed abstract class AuthProvider(val name: String)

object AuthProvider {
  val PromptKey = "prompt"
  val SelectAccount = "select_account"

  def forString(s: String): Either[SingleError, AuthProvider] =
    Seq(Google)
      .find(_.name == s)
      .toRight(SingleError(s"Unknown auth provider: '$s'."))

  def unapply(str: String): Option[AuthProvider] =
    forString(str).toOption

  case object Google extends AuthProvider("google")
}
