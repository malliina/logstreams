package com.malliina.logstreams.http4s

import cats.Applicative
import com.malliina.logstreams.Errors
import com.malliina.logstreams.http4s.BasicService.noCache
import org.http4s.CacheDirective.{`must-revalidate`, `no-cache`, `no-store`}
import org.http4s.{EntityEncoder, Request, Response, Status}
import org.http4s.headers.`Cache-Control`
import io.circe.syntax.EncoderOps

object BasicService:
  val noCache = `Cache-Control`(`no-cache`(), `no-store`, `must-revalidate`)

class BasicService[F[_]: Applicative] extends Implicits[F]:
  def ok[A](a: A)(implicit w: EntityEncoder[F, A]) = Ok(a, noCache)

  def notFound(req: Request[F]): F[Response[F]] =
    NotFound(Errors.single(s"Not found: '${req.uri}'.").asJson, noCache)

  def serverError(implicit a: Applicative[F]): F[Response[F]] =
    InternalServerError(Errors.single(s"Server error.").asJson, noCache)
