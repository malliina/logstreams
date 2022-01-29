package com.malliina.logstreams.http4s

import com.malliina.logstreams.{Errors, SingleError}
import com.malliina.values.ErrorMessage
import org.http4s.{ParseFailure, Query, QueryParamDecoder, QueryParameterValue}

object QueryParsers extends QueryParsers

trait QueryParsers:
  def parseOrDefault[T: QueryParamDecoder](q: Query, key: String, default: => T) =
    parseOpt[T](q, key).getOrElse(Right(default))

  def parse[T: QueryParamDecoder](q: Query, key: String) =
    parseOpt[T](q, key)
      .getOrElse(Left(Errors.single(s"Query key not found: '$key'.")))

  def parseOpt[T](q: Query, key: String)(implicit
    dec: QueryParamDecoder[T]
  ): Option[Either[Errors, T]] =
    q.params.get(key).map { g =>
      dec.decode(QueryParameterValue(g)).toEither.left.map { failures =>
        Errors(failures.map(pf => SingleError(pf.sanitized, "input")))
      }
    }

  def decoder[T](validate: String => Either[ErrorMessage, T]): QueryParamDecoder[T] =
    QueryParamDecoder.stringQueryParamDecoder.emap { s =>
      validate(s).left.map { err => parseFailure(err.message) }
    }

  def parseFailure(message: String) = ParseFailure(message, message)
