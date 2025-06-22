package com.malliina.logstreams

import com.malliina.http.Errors
import com.malliina.http4s.QueryParsers.parseOrDefault
import com.malliina.logstreams.Limits.DefaultLimit
import com.malliina.values.Literals.nonNeg
import com.malliina.values.NonNeg
import org.http4s.{ParseFailure, Query, QueryParamDecoder}

trait LimitsLike:
  def limit: NonNeg
  def offset: NonNeg

case class Limits(limit: NonNeg, offset: NonNeg):
  def prev = offset.minus(DefaultLimit.value).map(newOffset => Limits(limit, newOffset))
  def next = Limits(limit, offset + DefaultLimit)

object Limits:
  val Limit = "limit"
  val Offset = "offset"

  val DefaultLimit: NonNeg = 500.nonNeg
  private val DefaultOffset: NonNeg = 0.nonNeg

  val default = Limits(DefaultLimit, DefaultOffset)

  given QueryParamDecoder[NonNeg] = QueryParamDecoder.intQueryParamDecoder.emap: i =>
    NonNeg(i).left.map(err => ParseFailure(err.message, err.message))

  def apply(q: Query): Either[Errors, Limits] = for
    limit <- parseOrDefault(q, Limit, DefaultLimit)
    offset <- parseOrDefault(q, Offset, DefaultOffset)
  yield Limits(limit, offset)
