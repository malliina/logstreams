package com.malliina.logstreams.db

import com.malliina.logstreams.Errors
import com.malliina.logstreams.http4s.QueryParsers
import com.malliina.logstreams.models.LogLevel
import com.malliina.values.{ErrorMessage, Username, StringEnumCompanion}
import org.http4s.{Query, QueryParamDecoder}

case class StreamsQuery(
  apps: Seq[Username],
  level: LogLevel,
  limit: Int,
  offset: Int,
  order: SortOrder,
  query: Option[String]
) {
  def queryStar = query.map(q => s"$q*")
}

object StreamsQuery {
  val AppKey = "app"
  val Limit = "limit"
  val Offset = "offset"
  val Query = "q"

  val default = StreamsQuery(Nil, LogLevel.Info, 1000, 0, SortOrder.default, None)

  def fromQuery(q: Query): Either[Errors, StreamsQuery] = for {
    apps <- Right(q.multiParams.getOrElse(AppKey, Nil).map(s => Username(s)))
    level <-
      LogLevel
        .build(q.params.getOrElse(LogLevel.Key, LogLevel.Info.name))
        .left
        .map(msg => Errors(msg))
    limit <- QueryParsers.parseOrDefault[Int](q, Limit, 500)
    offset <- QueryParsers.parseOrDefault[Int](q, Offset, 0)
    sort <- SortOrder.fromQuery(q)
    query <- QueryParsers.parseOpt[String](q, Query).map(_.map(Option.apply)).getOrElse(Right(None))
  } yield StreamsQuery(apps, level, limit, offset, sort, query.filter(_.length >= 3))
}

sealed abstract class SortOrder(val name: String) {
  override def toString: String = name
}

object SortOrder extends StringEnumCompanion[SortOrder] {
  val Order = "order"
  val asc: SortOrder = Ascending
  val desc: SortOrder = Descending
  val default: SortOrder = desc

  val all: Seq[SortOrder] = Seq(asc, desc)

  case object Ascending extends SortOrder("asc")
  case object Descending extends SortOrder("desc")

  implicit val queryDecoder: QueryParamDecoder[SortOrder] = QueryParsers.decoder[SortOrder](build)

  override def write(t: SortOrder): String = t.name

  def fromQuery(q: Query) = QueryParsers.parseOrDefault(q, Order, default)
}
