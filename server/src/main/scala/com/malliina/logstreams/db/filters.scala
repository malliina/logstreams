package com.malliina.logstreams.db

import com.malliina.values.{ErrorMessage, Username, ValidatingCompanion}
import play.api.mvc.{QueryStringBindable, RequestHeader}

case class StreamsQuery(apps: Seq[Username], limit: Int, offset: Int, order: SortOrder)

object StreamsQuery {
  val AppKey = "app"
  val Limit = "limit"
  val Offset = "offset"

  val default = StreamsQuery(Nil, 1000, 0, SortOrder.default)

  val bindableUser = QueryStringBindable.bindableString.transform[Username](Username.apply, _.name)
  val bindableUsers = QueryStringBindable.bindableSeq[Username](bindableUser)

  def apply(rh: RequestHeader): Either[ErrorMessage, StreamsQuery] = {
    def readIntOrElse(key: String, default: Int): Either[ErrorMessage, Int] =
      QueryStringBindable.bindableInt
        .bind(key, rh.queryString)
        .getOrElse(Right(default))
        .left
        .map(ErrorMessage.apply)

    val bindApps =
      bindableUsers.bind(AppKey, rh.queryString).getOrElse(Right(Nil)).left.map(ErrorMessage.apply)
    for {
      apps <- bindApps
      limit <- readIntOrElse(Limit, 500)
      offset <- readIntOrElse(Offset, 0)
      order <- SortOrder(rh)
    } yield StreamsQuery(apps, limit, offset, order)
  }
}

sealed abstract class SortOrder(val name: String) {
  override def toString = name
}

object SortOrder extends ValidatingCompanion[String, SortOrder] {
  val Order = "order"
  val asc = Ascending
  val desc = Descending
  val default = desc

  val all = Seq(asc, desc)

  override def build(input: String): Either[ErrorMessage, SortOrder] =
    all
      .find(_.name.toLowerCase == input.toLowerCase)
      .toRight(ErrorMessage(s"Invalid input: '$input'. Must be one of: '${all.mkString(", ")}'."))

  override def write(t: SortOrder): String = t.name

  def apply(rh: RequestHeader): Either[ErrorMessage, SortOrder] =
    bindString(Order, build, desc, rh)

  def bindString[T](
    key: String,
    validate: String => Either[ErrorMessage, T],
    default: T,
    rh: RequestHeader
  ): Either[ErrorMessage, T] =
    QueryStringBindable.bindableString
      .bind(key, rh.queryString)
      .map(e => e.flatMap(validate))
      .getOrElse(Right(default))
}

case object Ascending extends SortOrder("asc")
case object Descending extends SortOrder("desc")
