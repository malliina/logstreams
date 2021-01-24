package com.malliina.logstreams.db

import com.malliina.logstreams.Errors
import com.malliina.logstreams.http4s.QueryParsers
import com.malliina.values.{ErrorMessage, Username, ValidatingCompanion}
import org.http4s.{Query, QueryParamDecoder}

case class StreamsQuery(apps: Seq[Username], limit: Int, offset: Int, order: SortOrder)

object StreamsQuery {
  val AppKey = "app"
  val Limit = "limit"
  val Offset = "offset"

  val default = StreamsQuery(Nil, 1000, 0, SortOrder.default)

  def fromQuery(q: Query): Either[Errors, StreamsQuery] = for {
//    apps <- QueryParsers.parseOrDefault[List[Username]](q, AppKey, Nil)
    limit <- QueryParsers.parseOrDefault[Int](q, Limit, 500)
    offset <- QueryParsers.parseOrDefault[Int](q, Offset, 0)
    sort <- SortOrder.fromQuery(q)
  } yield StreamsQuery(Nil, limit, offset, sort)
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

  def fromQuery(q: Query) = QueryParsers.parseOrDefault(q, Order, default)
}

case object Ascending extends SortOrder("asc")
case object Descending extends SortOrder("desc")
