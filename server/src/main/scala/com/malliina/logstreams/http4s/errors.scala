package com.malliina.logstreams.http4s

import com.malliina.web.AuthError
import io.circe.{DecodingFailure, Json}
import org.http4s.Headers

class MissingCredentialsException(error: MissingCredentials) extends IdentityException(error)

class IdentityException(val error: IdentityError) extends Exception

class JsonException(val error: io.circe.Error, val message: String)
  extends Exception(s"JSON exception $error for '$message'.")

object IdentityException:
  def apply(error: IdentityError): IdentityException = error match
    case mc @ MissingCredentials(_, _) => new MissingCredentialsException(mc)
    case other                         => new IdentityException(other)

sealed trait IdentityError:
  def headers: Headers

case class MissingCredentials(message: String, headers: Headers) extends IdentityError
case class JWTError(error: AuthError, headers: Headers) extends IdentityError
