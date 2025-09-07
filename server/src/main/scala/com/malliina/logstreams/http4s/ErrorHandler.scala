package com.malliina.logstreams.http4s

import cats.effect.Async
import com.malliina.http.{HttpResponse, ResponseException}
import com.malliina.util.AppLogger
import org.http4s.Response

import scala.util.control.NonFatal

class ErrorHandler[F[_]: Async] extends LogsService[F]:
  private val log = AppLogger(getClass)

  def partial: PartialFunction[Throwable, F[Response[F]]] =
    case re: ResponseException =>
      val error = re.error
      error.response match
        case res: HttpResponse =>
          log.error(s"HTTP ${error.code} for '${error.url}'. Body: '${res.asString}'.")
        case _ =>
          log.error(s"HTTP ${error.code} for '${error.url}'.")
      serverError
    case NonFatal(t) =>
      log.error(s"Server error.", t)
      serverError
