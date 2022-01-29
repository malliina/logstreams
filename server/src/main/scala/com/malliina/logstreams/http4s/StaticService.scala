package com.malliina.logstreams.http4s

import cats.data.NonEmptyList
import cats.effect.Sync
import cats.implicits.*
import com.malliina.logstreams.HashedAssets
import com.malliina.logstreams.http4s.StaticService.log
import com.malliina.util.AppLogger
import com.malliina.values.UnixPath
import org.http4s.CacheDirective.{`max-age`, `no-cache`, `public`}
import org.http4s.headers.`Cache-Control`
import org.http4s.{HttpRoutes, Request, StaticFile}

import scala.concurrent.duration.DurationInt

object StaticService:
  private val log = AppLogger(getClass)

  def apply[F[_]]()(implicit s: Sync[F]): StaticService[F] = new StaticService[F]()(s)

class StaticService[F[_]]()(implicit s: Sync[F]) extends BasicService[F]:
  val fontExtensions = List(".woff", ".woff2", ".eot", ".ttf")
  val supportedStaticExtensions =
    List(".html", ".js", ".map", ".css", ".png", ".ico") ++ fontExtensions

  val prefix = HashedAssets.prefix
  //  val routes = resourceService[F](ResourceService.Config("/db", blocker))
  //  val routes = fileService(FileService.Config("./public", blocker))
  val routes = HttpRoutes.of[F] {
    case req @ GET -> rest if supportedStaticExtensions.exists(rest.toString.endsWith) =>
      val file = UnixPath(rest.segments.map(_.decoded()).mkString("/"))
      val isCacheable = file.value.count(_ == '.') == 2 || file.value.startsWith("static/")
      val cacheHeaders =
        if isCacheable then NonEmptyList.of(`max-age`(365.days), `public`)
        else NonEmptyList.of(`no-cache`())
      val res = s"/$prefix/$file"
      log.debug(s"Searching for '$file' at resource '$res'...")
      StaticFile
        .fromResource(res, Option(req))
        .map(_.putHeaders(`Cache-Control`(cacheHeaders)))
        .fold(onNotFound(req))(_.pure[F])
        .flatten
  }

  private def onNotFound(req: Request[F]) =
    log.info(s"Not found '${req.uri}'.")
    notFound(req)
