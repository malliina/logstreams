package com.malliina.logstreams.http4s

import cats.data.NonEmptyList
import cats.effect.{Async, Sync}
import cats.implicits.*
import com.malliina.app.BuildInfo
import com.malliina.logstreams.{HashedAssets, LocalConf}
import com.malliina.logstreams.http4s.StaticService.log
import com.malliina.util.AppLogger
import com.malliina.values.UnixPath
import fs2.io.file.{Path as FS2Path}
import org.http4s.CacheDirective.{`max-age`, `no-cache`, `public`}
import org.http4s.headers.`Cache-Control`
import org.http4s.{Header, HttpRoutes, Request, StaticFile}
import org.typelevel.ci.CIStringSyntax

import java.nio.file.Files
import scala.concurrent.duration.DurationInt

object StaticService:
  private val log = AppLogger(getClass)

class StaticService[F[_]: Async] extends BasicService[F]:
  private val fontExtensions = List(".woff", ".woff2", ".eot", ".ttf")
  private val imageExtensions = Seq(".png", ".ico", ".svg")
  private val webExtensions = Seq(".html", ".js", ".map", ".css")
  private val supportedStaticExtensions = webExtensions ++ imageExtensions ++ fontExtensions

  private val publicDir = FS2Path(BuildInfo.assetsDir)
  private val allowAllOrigins = Header.Raw(ci"Access-Control-Allow-Origin", "*")
  val routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ GET -> rest if supportedStaticExtensions.exists(rest.toString.endsWith) =>
      val file = UnixPath(rest.segments.mkString("/"))
      val isCacheable = file.value.count(_ == '.') == 2 || file.value.startsWith("static/")
      val cacheHeaders =
        if isCacheable then NonEmptyList.of(`max-age`(365.days), `public`)
        else NonEmptyList.of(`no-cache`())
      val assetPath: FS2Path = publicDir.resolve(file.value)
      val exists = Files.exists(assetPath.toNioPath)
      val isReadable = Files.isReadable(assetPath.toNioPath)
      val resourcePath = s"${BuildInfo.publicFolder}/${file.value}"
      log.info(s"Searching for resource '$resourcePath' or else file '$assetPath'.")
      findAsset(resourcePath, assetPath, req)
        .map(_.putHeaders(`Cache-Control`(cacheHeaders), allowAllOrigins))
        .fold(onNotFound(req))(_.pure[F])
        .flatten
  }

  private def findAsset(resourcePath: String, filePath: FS2Path, req: Request[F]) =
    if LocalConf.isProd then
      StaticFile
        .fromResource(resourcePath, Option(req))
        .orElse(StaticFile.fromPath(filePath, Option(req)))
    else
      StaticFile
        .fromPath(filePath, Option(req))
        .orElse(StaticFile.fromResource(resourcePath, Option(req)))

  private def onNotFound(req: Request[F]) =
    log.info(s"Not found '${req.uri}'.")
    notFound(req)
