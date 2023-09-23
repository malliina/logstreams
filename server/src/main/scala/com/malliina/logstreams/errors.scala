package com.malliina.logstreams

import cats.data.NonEmptyList
import com.malliina.values.ErrorMessage
import io.circe.*

case class SingleError(message: String, key: String) derives Codec.AsObject

object SingleError:
  def apply(message: String): SingleError = apply(message, "generic")

case class Errors(errors: NonEmptyList[SingleError]) derives Codec.AsObject

object Errors:
  given [T: Codec]: Codec[NonEmptyList[T]] = Codec.from(
    Decoder.decodeNonEmptyList[T],
    Encoder.encodeNonEmptyList[T]
  )

  def apply(message: ErrorMessage): Errors = Errors.single(message.message)
  def single(message: String): Errors = Errors(NonEmptyList.of(SingleError(message)))
