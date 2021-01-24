package com.malliina.logstreams.db

import com.malliina.logstreams.Errors
import com.malliina.logstreams.http4s.QueryParsers
import com.malliina.values.{ErrorMessage, Username, ValidatingCompanion}
import org.http4s.{Query, QueryParamDecoder}
import play.api.mvc.{QueryStringBindable, RequestHeader}

case class StreamsQuery(apps: Seq[Username], limit: Int, offset: Int, order: SortOrder)

object StreamsQuery {
  val AppKey = "app"
  val Limit = "limit"
  val Offset = "offset"

  val default = StreamsQuery(Nil, 1000, 0, SortOrder.default)

  val bindableUser = QueryStringBindable.bindableString.transform[Username](Username.apply, _.name)
  val bindableUsers = QueryStringBindable.bindableSeq[Username](bindableUser)

//  implicit val userDecoder = QueryParsers.decoder[Username](s => Username.build(s))
//  QueryParamDecoder.stringQueryParamDecoder.

  def fromQuery(q: Query): Either[Errors, StreamsQuery] = for {
//    apps <- QueryParsers.parseOrDefault[List[Username]](q, AppKey, Nil)
    limit <- QueryParsers.parseOrDefault[Int](q, Limit, 500)
    offset <- QueryParsers.parseOrDefault[Int](q, Offset, 0)
    sort <- SortOrder.fromQuery(q)
  } yield StreamsQuery(Nil, limit, offset, sort)

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
  val asc: SortOrder = Ascending
  val desc: SortOrder = Descending
  val default: SortOrder = desc

  val all: Seq[SortOrder] = Seq(asc, desc)

  implicit val queryDecoder = QueryParsers.decoder[SortOrder](build)

  override def build(input: String): Either[ErrorMessage, SortOrder] =
    all
      .find(_.name.toLowerCase == input.toLowerCase)
      .toRight(ErrorMessage(s"Invalid input: '$input'. Must be one of: '${all.mkString(", ")}'."))

  override def write(t: SortOrder): String = t.name

  def apply(rh: RequestHeader): Either[ErrorMessage, SortOrder] =
    bindString(Order, build, default, rh)

  def fromQuery(q: Query) = QueryParsers.parseOrDefault(q, Order, default)

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
