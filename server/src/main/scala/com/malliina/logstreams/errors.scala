package com.malliina.logstreams

import cats.data.NonEmptyList
import com.malliina.values.ErrorMessage
import com.malliina.web.JWTError
import play.api.libs.json.{Format, JsError, JsSuccess, Json, Reads, Writes}

case class SingleError(message: String, key: String)

object SingleError {
  implicit val json = Json.format[SingleError]

  def apply(message: String): SingleError = apply(message, "generic")

  def forJWT(error: JWTError): SingleError =
    SingleError(error.message.message, error.key)
}

case class Errors(errors: NonEmptyList[SingleError])

object Errors {
  implicit val se = SingleError.json
  import cats.implicits._
  implicit def nelJson[T: Format]: Format[NonEmptyList[T]] =
    Format(
      Reads { json =>
        json
          .validate[List[T]]
          .flatMap(_.toNel.map(t => JsSuccess(t)).getOrElse(JsError(s"Empty list: '$json'.")))
      },
      Writes.list[T].contramap(_.toList)
    )

  implicit val json = Json.format[Errors]

  def apply(message: ErrorMessage): Errors = Errors.single(message.message)
  def single(message: String): Errors = Errors(NonEmptyList.of(SingleError(message)))
}
