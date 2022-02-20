package com.malliina.logstreams.http4s

import cats.data.NonEmptyList
import cats.effect.{Async, Sync}
import cats.implicits.*
import com.malliina.app.BuildInfo
import com.malliina.logstreams.HashedAssets
import com.malliina.logstreams.http4s.StaticService.log
import com.malliina.util.AppLogger
import com.malliina.values.UnixPath
import org.http4s.CacheDirective.{`max-age`, `no-cache`, `public`}
import org.http4s.headers.`Cache-Control`
import org.http4s.{HttpRoutes, Request, StaticFile}

import java.nio.file.Files
import scala.concurrent.duration.DurationInt

object StaticService:
  private val log = AppLogger(getClass)

class StaticService[F[_]: Async] extends BasicService[F]:
  private val fontExtensions = List(".woff", ".woff2", ".eot", ".ttf")
  private val supportedStaticExtensions =
    List(".html", ".js", ".map", ".css", ".png", ".ico") ++ fontExtensions

  private val publicDir = fs2.io.file.Path(BuildInfo.assetsDir)
  val routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ GET -> rest if supportedStaticExtensions.exists(rest.toString.endsWith) =>
      val file = UnixPath(rest.segments.mkString("/"))
      val isCacheable = file.value.count(_ == '.') == 2 || file.value.startsWith("static/")
      val cacheHeaders =
        if isCacheable then NonEmptyList.of(`max-age`(365.days), `public`)
        else NonEmptyList.of(`no-cache`())
      val assetPath: fs2.io.file.Path = publicDir.resolve(file.value)
      val exists = Files.exists(assetPath.toNioPath)
      val isReadable = Files.isReadable(assetPath.toNioPath)
      log.info(s"Searching for '$assetPath'. Exists: $exists. Is readable: $isReadable.")
      StaticFile
        .fromPath(assetPath, Option(req))
        .map(_.putHeaders(`Cache-Control`(cacheHeaders)))
        .fold(onNotFound(req))(_.pure[F])
        .flatten
  }

  private def onNotFound(req: Request[F]) =
    log.info(s"Not found '${req.uri}'.")
    notFound(req)
