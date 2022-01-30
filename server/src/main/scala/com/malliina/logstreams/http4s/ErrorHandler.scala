package com.malliina.logstreams.http4s

import cats.Monad
import com.malliina.http.ResponseException
import com.malliina.util.AppLogger
import org.http4s.headers.{Connection, `Content-Length`}
import org.http4s.{Headers, Request, Response, Status}
import org.typelevel.ci.CIStringSyntax

import scala.util.control.NonFatal

object ErrorHandler:
  private val log = AppLogger(getClass)

  def apply[F[_], G[_]](implicit
    F: Monad[F]
  ): Request[G] => PartialFunction[Throwable, F[Response[G]]] =
    req => {
      case re: ResponseException =>
        val error = re.error
        log.error(s"HTTP ${error.code} for '${error.url}'. Body: '${error.response.asString}'.")
        internalServerError(req)
      case NonFatal(t) =>
        log.error(s"Server error: ${req.method} ${req.pathInfo}. Exception $t", t)
        internalServerError(req)
    }

  private def internalServerError[F[_], G[_]](
    req: Request[G]
  )(implicit F: Monad[F]): F[Response[G]] =
    F.pure(
      Response(
        Status.InternalServerError,
        req.httpVersion,
        Headers(Connection(ci"close"), `Content-Length`.zero)
      )
    )
