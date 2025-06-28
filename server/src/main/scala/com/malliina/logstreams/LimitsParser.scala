package com.malliina.logstreams

import com.malliina.http.Errors
import com.malliina.http4s.QueryParsers.parseOrDefault
import com.malliina.logstreams.models.Limits
import com.malliina.logstreams.models.Limits.{DefaultLimit, DefaultOffset, Limit, Offset}
import com.malliina.values.NonNeg
import org.http4s.{ParseFailure, Query, QueryParamDecoder}

object LimitsParser:
  given QueryParamDecoder[NonNeg] = QueryParamDecoder.intQueryParamDecoder.emap: i =>
    NonNeg(i).left.map(err => ParseFailure(err.message, err.message))

  def apply(q: Query): Either[Errors, Limits] = for
    limit <- parseOrDefault(q, Limit, DefaultLimit)
    offset <- parseOrDefault(q, Offset, DefaultOffset)
  yield Limits(limit, offset)
