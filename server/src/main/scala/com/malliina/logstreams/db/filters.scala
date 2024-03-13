package com.malliina.logstreams.db

import com.malliina.logback.TimeFormatter
import com.malliina.logstreams.Errors
import com.malliina.logstreams.http4s.QueryParsers
import com.malliina.logstreams.models.{AppName, LogLevel, Queries}
import com.malliina.values.{StringEnumCompanion, Username}
import org.http4s.{Query, QueryParamDecoder}

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDate, OffsetDateTime}
import scala.concurrent.duration.DurationInt

case class TimeRange(from: Option[Instant], to: Option[Instant]):
  def isEmpty = from.isEmpty && to.isEmpty
  def describe = (from, to) match
    case (Some(f), Some(t)) => s"[f - $t]"
    case (None, Some(t))    => s"(- $t]"
    case (Some(f), None)    => s"[$f -)"
    case other              => ""

object TimeRange:
  private val From = Queries.From
  private val To = Queries.To

  val none = TimeRange(None, None)
  private val instantDecoder =
    QueryParamDecoder.instantQueryParamDecoder(DateTimeFormatter.ISO_INSTANT)
  private val localDateEncoder =
    QueryParamDecoder.localDate(DateTimeFormatter.ISO_LOCAL_DATE)
  private val offsetDateTimeEncoder =
    QueryParamDecoder.offsetDateTime(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

  def recent(now: Instant): TimeRange =
    since(now.minus(5.minutes.toSeconds, ChronoUnit.SECONDS))

  def since(from: Instant): TimeRange =
    TimeRange(Option(from), None)

  def apply(q: Query): Either[Errors, TimeRange] =
    for
      from <- bindInstant(From, q)
      to <- bindInstant(To, q)
    yield TimeRange(from, to)

  private def bindInstant(key: String, q: Query): Either[Errors, Option[Instant]] =
    QueryParsers
      .parseOptE[Instant](q, key)(using instantDecoder)
      .orElse(
        QueryParsers
          .parseOptE[LocalDate](q, key)(using localDateEncoder)
          .map(_.map(_.atStartOfDay(TimeFormatter.helsinki).toInstant))
      )
      .orElse(
        QueryParsers
          .parseOptE[OffsetDateTime](q, key)(using offsetDateTimeEncoder)
          .map(_.map(_.toInstant))
      )

case class StreamsQuery(
  apps: Seq[Username],
  level: LogLevel,
  timeRange: TimeRange,
  limit: Int,
  offset: Int,
  order: SortOrder,
  query: Option[String]
):
  def queryStar = query.map(q => s"$q*")

object StreamsQuery:
  val AppKey = AppName.Key
  val Limit = "limit"
  val Offset = "offset"
  val Query = Queries.Q

  def default = StreamsQuery(
    Nil,
    LogLevel.Info,
    TimeRange.recent(Instant.now().minus(48, ChronoUnit.HOURS)),
    1000,
    0,
    SortOrder.default,
    None
  )

  def fromQuery(q: Query): Either[Errors, StreamsQuery] = for
    apps <- Right(q.multiParams.getOrElse(AppKey, Nil).map(s => Username(s)))
    level <-
      LogLevel
        .build(q.params.getOrElse(LogLevel.Key, LogLevel.Info.name))
        .left
        .map(msg => Errors(msg))
    timeRange <- TimeRange(q)
    limit <- QueryParsers.parseOrDefault[Int](q, Limit, 500)
    offset <- QueryParsers.parseOrDefault[Int](q, Offset, 0)
    sort <- SortOrder.fromQuery(q)
    query <- QueryParsers.parseOpt[String](q, Query).map(_.map(Option.apply)).getOrElse(Right(None))
  yield StreamsQuery(apps, level, timeRange, limit, offset, sort, query.filter(_.length >= 3))

sealed abstract class SortOrder(val name: String):
  override def toString: String = name

object SortOrder extends StringEnumCompanion[SortOrder]:
  val Order = "order"
  val asc: SortOrder = Ascending
  val desc: SortOrder = Descending
  val default: SortOrder = desc

  val all: Seq[SortOrder] = Seq(asc, desc)

  case object Ascending extends SortOrder("asc")
  case object Descending extends SortOrder("desc")

  given QueryParamDecoder[SortOrder] = QueryParsers.decoder[SortOrder](build)

  override def write(t: SortOrder): String = t.name

  def fromQuery(q: Query) = QueryParsers.parseOrDefault(q, Order, default)
