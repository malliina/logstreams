package com.malliina.logstreams

import cats.data.NonEmptyList
import com.malliina.values.ErrorMessage
import com.malliina.web.JWTError
import io.circe.*
import io.circe.generic.semiauto.*
import io.circe.syntax.*

case class SingleError(message: String, key: String)

object SingleError:
  implicit val json: Codec[SingleError] = deriveCodec[SingleError]

  def apply(message: String): SingleError = apply(message, "generic")

  def forJWT(error: JWTError): SingleError =
    SingleError(error.message.message, error.key)

case class Errors(errors: NonEmptyList[SingleError])

object Errors:
  implicit val se: Codec[SingleError] = SingleError.json
  import cats.implicits.*
  implicit def nel[T: Codec]: Codec[NonEmptyList[T]] = Codec.from(
    Decoder.decodeNonEmptyList[T],
    Encoder.encodeNonEmptyList[T]
  )
  implicit val json: Codec[Errors] = deriveCodec[Errors]

  def apply(message: ErrorMessage): Errors = Errors.single(message.message)
  def single(message: String): Errors = Errors(NonEmptyList.of(SingleError(message)))
