package com.malliina.logstreams.http4s

import cats.data.Kleisli
import cats.effect.concurrent.Ref
import cats.effect.{Blocker, ExitCode, IO, IOApp, Resource}
import com.malliina.app.AppMeta
import com.malliina.http.io.HttpClientIO
import com.malliina.logstreams.auth.{AuthBuilder, Auther, Auths, JWT}
import com.malliina.logstreams.db.{DoobieDatabase, DoobieDatabaseAuth, DoobieStreamsDatabase}
import com.malliina.logstreams.html.{HashedAssetsSource, Htmls}
import com.malliina.logstreams.models.{LogEntryInputs, LogSources}
import com.malliina.logstreams.{AppMode, LogstreamsConf, SourceMessage}
import com.malliina.util.AppLogger
import com.malliina.web.GoogleAuthFlow
import fs2.concurrent.Topic
import org.http4s.server.{Router, Server}
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.HSTS
import org.http4s.{HttpRoutes, Request, Response}

import scala.concurrent.ExecutionContext

case class ServerComponents(
  app: Service,
  handler: Kleisli[IO, Request[IO], Response[IO]],
  server: Server[IO]
)

object Server extends IOApp {
  val log = AppLogger(getClass)
  val port = 9000

  def server(
    conf: LogstreamsConf,
    authBuilder: AuthBuilder,
    port: Int = port
  ): Resource[IO, ServerComponents] = for {
    blocker <- Blocker[IO]
    service <- appService(conf, authBuilder)
    handler = makeHandler(service, blocker)
    _ <- Resource.liftF(
      IO(log.info(s"Binding on port $port using app version ${AppMeta.ThisApp.git}..."))
    )
    server <- BlazeServerBuilder[IO](ExecutionContext.global)
      .bindHttp(port = port, "0.0.0.0")
      .withHttpApp(handler)
      .resource
  } yield ServerComponents(service, handler, server)

  def appService(conf: LogstreamsConf, authBuilder: AuthBuilder): Resource[IO, Service] = for {
    blocker <- Blocker[IO]
    db <- DoobieDatabase.withMigrations(conf.db, blocker)
    logsTopic <- Resource.liftF(Topic[IO, LogEntryInputs](LogEntryInputs(Nil)))
    adminsTopic <- Resource.liftF(Topic[IO, LogSources](LogSources(Nil)))
    connecteds <- Resource.liftF(Ref[IO].of(LogSources(Nil)))
  } yield {
    val logsDatabase = DoobieStreamsDatabase(db)
    val users = DoobieDatabaseAuth(db)
    val auths: Auther = authBuilder(users, Http4sAuth(JWT(conf.secret)))
    val sockets = new LogSockets(logsTopic, adminsTopic, connecteds, logsDatabase)
    val google = GoogleAuthFlow(conf.google, HttpClientIO())
    Service(
      db,
      users,
      Htmls.forApp("frontend", conf.mode == AppMode.Prod, HashedAssetsSource),
      auths,
      sockets,
      google
    )
  }

  def makeHandler(service: Service, blocker: Blocker) = HSTS {
    orNotFound {
      Router(
        "/" -> service.routes,
        "/assets" -> StaticService(blocker, contextShift).routes
      )
    }
  }

  def orNotFound(rs: HttpRoutes[IO]): Kleisli[IO, Request[IO], Response[IO]] =
    Kleisli(req => rs.run(req).getOrElseF(BasicService.notFound(req)))

  override def run(args: List[String]): IO[ExitCode] =
    server(LogstreamsConf.load, Auths).use(_ => IO.never).as(ExitCode.Success)
}
