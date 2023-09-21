package com.malliina.logstreams.http4s

import cats.data.NonEmptyList
import cats.effect.Async
import cats.implicits.*
import com.malliina.app.BuildInfo
import com.malliina.logstreams.http4s.StaticService.log
import com.malliina.util.AppLogger
import com.malliina.values.UnixPath
import fs2.io.file.Path as FS2Path
import org.http4s.CacheDirective.{`max-age`, `must-revalidate`, `no-cache`, `no-store`, `public`}
import org.http4s.headers.`Cache-Control`
import org.http4s.{Header, HttpRoutes, Request, StaticFile}
import org.typelevel.ci.CIStringSyntax

import scala.concurrent.duration.DurationInt

object StaticService:
  private val log = AppLogger(getClass)

class StaticService[F[_]: Async] extends BasicService[F]:
  private val fontExtensions = List(".woff", ".woff2", ".eot", ".ttf")
  private val imageExtensions = Seq(".png", ".ico", ".svg")
  private val webExtensions = Seq(".html", ".js", ".map", ".css")
  private val supportedStaticExtensions = webExtensions ++ imageExtensions ++ fontExtensions

  private val publicDir = FS2Path.fromNioPath(BuildInfo.assetsDir.toPath)
  private val allowAllOrigins = Header.Raw(ci"Access-Control-Allow-Origin", "*")

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ GET -> rest if supportedStaticExtensions.exists(rest.toString.endsWith) =>
      val file = UnixPath(rest.segments.mkString("/"))
      val isCacheable = file.value.count(_ == '.') == 2 || file.value.startsWith("static/")
      val cacheHeaders =
        if isCacheable then NonEmptyList.of(`max-age`(365.days), `public`)
        else NonEmptyList.of(`no-cache`(), `no-store`, `must-revalidate`)
      val assetPath: FS2Path = publicDir.resolve(file.value)
      val resourcePath = s"${BuildInfo.publicFolder}/${file.value}"
      val path = if BuildInfo.isProd then resourcePath else assetPath.toNioPath.toAbsolutePath
      log.debug(s"Searching for '$path'...")
      val search =
        if BuildInfo.isProd then StaticFile.fromResource(resourcePath, Option(req))
        else StaticFile.fromPath(assetPath, Option(req))
      search
        .map(_.putHeaders(`Cache-Control`(cacheHeaders), allowAllOrigins))
        .fold(onNotFound(req))(_.pure[F])
        .flatten
  }

  private def onNotFound(req: Request[F]) =
    log.info(s"Not found '${req.uri}'.")
    notFound(req)
