package com.malliina.logstreams.http4s

import cats.data.Kleisli
import cats.effect.kernel.Ref
import cats.effect.{ExitCode, IO, IOApp, Resource}
import com.malliina.app.AppMeta
import com.malliina.http.io.HttpClientIO
import com.malliina.logstreams.auth.{AuthBuilder, Auther, Auths, JWT}
import com.malliina.logstreams.db.{DoobieDatabase, DoobieDatabaseAuth, DoobieStreamsDatabase}
import com.malliina.logstreams.html.{HashedAssetsSource, Htmls}
import com.malliina.logstreams.models.{AppLogEvents, LogEntryInputs, LogSources}
import com.malliina.logstreams.{AppMode, LogstreamsConf}
import com.malliina.util.AppLogger
import com.malliina.web.GoogleAuthFlow
import fs2.concurrent.Topic
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.middleware.{GZip, HSTS}
import org.http4s.server.{Router, Server}
import org.http4s.{HttpRoutes, Request, Response}
import org.http4s.server.websocket.WebSocketBuilder2

import scala.concurrent.ExecutionContext

case class ServerComponents(
  app: Service,
//  handler: Kleisli[IO, Request[IO], Response[IO]],
  server: Server
)

object Server extends IOApp {
  val log = AppLogger(getClass)
  val port = 9000

  def server(
    conf: LogstreamsConf,
    authBuilder: AuthBuilder,
    port: Int = port
  ): Resource[IO, ServerComponents] = for {
    service <- appService(conf, authBuilder)
    _ <- Resource.eval(
      IO(log.info(s"Binding on port $port using app version ${AppMeta.ThisApp.git}..."))
    )
    server <-
      BlazeServerBuilder[IO]
        .bindHttp(port = port, "0.0.0.0")
        .withHttpWebSocketApp(socketBuilder => makeHandler(service, socketBuilder))
        .resource
  } yield ServerComponents(service, server)

  def appService(conf: LogstreamsConf, authBuilder: AuthBuilder): Resource[IO, Service] = for {
    db <- DoobieDatabase.withMigrations(conf.db)
    logsTopic <- Resource.eval(Topic[IO, LogEntryInputs])
    adminsTopic <- Resource.eval(Topic[IO, LogSources])
    connecteds <- Resource.eval(Ref[IO].of(LogSources(Nil)))
    logUpdates <- Resource.eval(Topic[IO, AppLogEvents])
    logsDatabase = DoobieStreamsDatabase(db)
    sockets = new LogSockets(logsTopic, adminsTopic, connecteds, logUpdates, logsDatabase)
    _ <- fs2.Stream.emit(()).concurrently(sockets.publisher).compile.resource.lastOrError
  } yield {
    val users = DoobieDatabaseAuth(db)
    val auths: Auther = authBuilder(users, Http4sAuth(JWT(conf.secret)))
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

  def makeHandler(service: Service, socketBuilder: WebSocketBuilder2[IO]) = GZip {
    HSTS {
      orNotFound {
        Router(
          "/" -> service.routes(socketBuilder),
          "/assets" -> StaticService[IO]().routes
        )
      }
    }
  }

  def orNotFound(rs: HttpRoutes[IO]): Kleisli[IO, Request[IO], Response[IO]] =
    Kleisli(req => rs.run(req).getOrElseF(BasicService.notFound(req)))

  override def run(args: List[String]): IO[ExitCode] =
    server(LogstreamsConf.parse(), Auths).use(_ => IO.never).as(ExitCode.Success)
}
