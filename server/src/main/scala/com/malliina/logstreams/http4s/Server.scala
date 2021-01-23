package com.malliina.logstreams.http4s

import cats.data.Kleisli
import cats.effect.{Blocker, ExitCode, IO, IOApp}
import com.malliina.app.AppMeta
import com.malliina.logstreams.LogstreamsConf
import com.malliina.logstreams.html.Htmls
import com.malliina.logstreams.auth.JWT
import com.malliina.logstreams.db.{DoobieDatabase, DoobieDatabaseAuth, DoobieStreamsDatabase}
import com.malliina.util.AppLogger
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.HSTS
import org.http4s.{HttpRoutes, Request, Response}
import scala.concurrent.ExecutionContext
import com.malliina.logstreams.auth.Auths

object Server extends IOApp {
  val log = AppLogger(getClass)
  val port = 9000

  def server(conf: LogstreamsConf) = for {
    picsApp <- appResource(conf)
    _ = log.info(s"Binding on port $port using app version ${AppMeta.ThisApp.git}...")
    server <- BlazeServerBuilder[IO](ExecutionContext.global)
      .bindHttp(port = port, "0.0.0.0")
      .withHttpApp(picsApp)
      .resource
  } yield server

  def appResource(conf: LogstreamsConf) = for {
    blocker <- Blocker[IO]
    db <- DoobieDatabase.withMigrations(conf.db, blocker)
  } yield {
    val logsDatabase = DoobieStreamsDatabase(db)
    val users = DoobieDatabaseAuth(db)
    val auths = Auths(users, Http4sAuth(JWT(conf.jwt)))
    HSTS {
      orNotFound {
        Router("/" -> Service(users, Htmls.forApp("client", isProd = false), auths).routes)
      }
    }
  }

  def orNotFound(rs: HttpRoutes[IO]): Kleisli[IO, Request[IO], Response[IO]] =
    Kleisli(req => rs.run(req).getOrElseF(BasicService.notFound(req)))

  override def run(args: List[String]): IO[ExitCode] =
    server(LogstreamsConf.load).use(_ => IO.never).as(ExitCode.Success)
}
