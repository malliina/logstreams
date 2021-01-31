package com.malliina.logstreams.http4s

import cats.Applicative
import cats.effect.IO
import com.malliina.logstreams.Errors
import com.malliina.logstreams.http4s.BasicService.noCache
import org.http4s.CacheDirective.{`must-revalidate`, `no-cache`, `no-store`}
import org.http4s.{EntityEncoder, Request, Response}
import org.http4s.headers.`Cache-Control`
import play.api.libs.json.Json

object BasicService extends BasicService[IO] {
  val noCache = `Cache-Control`(`no-cache`(), `no-store`, `must-revalidate`)
}

class BasicService[F[_]: Applicative] extends Implicits[F] {
  def ok[A](a: A)(implicit w: EntityEncoder[F, A]) = Ok(a, noCache)

  def notFound(req: Request[F]): F[Response[F]] =
    NotFound(Json.toJson(Errors.single(s"Not found: '${req.uri}'.")))
}
