package com.malliina.logstreams.db

import cats.data.NonEmptyList
import com.malliina.http.Errors
import com.malliina.http4s.QueryParsers
import com.malliina.logback.TimeFormatter
import com.malliina.logstreams.Limits
import com.malliina.logstreams.models.{AppName, LogLevel, Queries}
import com.malliina.values.Literals.nonNeg
import com.malliina.values.{StringEnumCompanion, Username}
import org.http4s.{Query, QueryParamDecoder, QueryParamEncoder}

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

  val instantEncoder = QueryParamEncoder.instantQueryParamEncoder(DateTimeFormatter.ISO_INSTANT)
  private val localDateEncoder =
    QueryParamDecoder.localDate(DateTimeFormatter.ISO_LOCAL_DATE)
  private val offsetDateTimeEncoder =
    QueryParamDecoder.offsetDateTime(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

  def recent(now: Instant): TimeRange =
    since(now.minus(5.minutes.toSeconds, ChronoUnit.SECONDS))

  def since(from: Instant): TimeRange =
    TimeRange(Option(from), None)

  def apply(q: Query, now: Instant): Either[Errors, TimeRange] =
    for
      from <- bindInstant(From, q)
      to <- bindInstant(To, q)
    yield TimeRange(from.orElse(Option(now.minus(48, ChronoUnit.HOURS))), to)

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
  limits: Limits,
  order: SortOrder,
  query: Option[String]
):
  def limit = limits.limit
  def offset = limits.offset
  def queryStar = query.map(q => s"$q*")
  def describe: String =
    val appsList = if apps.nonEmpty then s"apps ${apps.mkString(", ")} " else ""
    val queryStr = query.map(q => s"query '$q' ").getOrElse("")
    s"$queryStr${appsList}level $level limit $limit offset $offset time ${timeRange.describe} order $order"

object StreamsQuery:
  val AppKey = AppName.Key
  val Limit = "limit"
  val Offset = "offset"
  val Query = Queries.Q

  def default = StreamsQuery(
    Nil,
    LogLevel.Info,
    TimeRange.recent(Instant.now().minus(48, ChronoUnit.HOURS)),
    Limits(1000.nonNeg, 0.nonNeg),
    SortOrder.default,
    None
  )

  def fromQuery(q: Query, now: Instant): Either[Errors, StreamsQuery] = for
    apps <- Right(q.multiParams.getOrElse(AppKey, Nil).map(s => Username(s)))
    level <-
      LogLevel
        .build(q.params.getOrElse(LogLevel.Key, LogLevel.Info.name))
        .left
        .map(msg => Errors(msg))
    timeRange <- TimeRange(q, now)
    limits <- Limits(q)
    sort <- SortOrder.fromQuery(q)
    query <- QueryParsers.parseOpt[String](q, Query).map(_.map(Option.apply)).getOrElse(Right(None))
  yield StreamsQuery(apps, level, timeRange, limits, sort, query.filter(_.length >= 3))

  def toQuery(q: StreamsQuery): Map[String, NonEmptyList[String]] =
    Map(
      Query -> q.query.toList,
      AppKey -> q.apps.map(_.name),
      LogLevel.Key -> Seq(q.level.name),
      Queries.From -> q.timeRange.from.map(i => TimeRange.instantEncoder.encode(i).value).toList,
      Queries.To -> q.timeRange.to.map(i => TimeRange.instantEncoder.encode(i).value).toList,
      Limits.Limit -> Seq(s"${q.limits.limit}"),
      Limits.Offset -> Seq(s"${q.limits.offset}"),
      SortOrder.Order -> Seq(q.order.name)
    ).flatMap: (k, vs) =>
      NonEmptyList
        .fromList(vs.toList)
        .map: list =>
          k -> list

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
