package com.malliina.logstreams.http4s

import cats.Monad
import cats.effect.Async
import com.malliina.http.ResponseException
import com.malliina.util.AppLogger
import org.http4s.headers.{Connection, `Content-Length`}
import org.http4s.{Headers, Request, Response, Status}
import org.typelevel.ci.CIStringSyntax

import scala.util.control.NonFatal

class ErrorHandler[F[_]: Async] extends BasicService[F]:
  private val log = AppLogger(getClass)

  def blaze: Request[F] => PartialFunction[Throwable, F[Response[F]]] =
    req => partial

  def partial: PartialFunction[Throwable, F[Response[F]]] =
    case re: ResponseException =>
      val error = re.error
      log.error(s"HTTP ${error.code} for '${error.url}'. Body: '${error.response.asString}'.")
      serverError
    case NonFatal(t) =>
      log.error(s"Server error.", t)
      serverError
