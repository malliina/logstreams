package com.malliina.logstreams.http4s

import cats.data.Kleisli
import cats.effect.kernel.Ref
import cats.effect.std.Dispatcher
import cats.effect.{ExitCode, IO, IOApp, Resource}
import com.comcast.ip4s.{Port, host, port}
import com.malliina.app.AppMeta
import com.malliina.http.io.HttpClientIO
import com.malliina.logstreams.auth.{AuthBuilder, Auther, Auths, JWT}
import com.malliina.logstreams.client.LogstreamsUtils
import com.malliina.logstreams.db.{DoobieDatabase, DoobieDatabaseAuth, DoobieStreamsDatabase}
import com.malliina.logstreams.html.{AssetsSource, Htmls}
import com.malliina.logstreams.models.{AppLogEvents, LogEntryInputs, LogSources}
import com.malliina.logstreams.{AppMode, LocalConf, LogConf, LogstreamsConf}
import com.malliina.util.AppLogger
import com.malliina.web.GoogleAuthFlow
import fs2.concurrent.Topic
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.{GZip, HSTS}
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.server.{Router, Server}
import org.http4s.{HttpRoutes, Request, Response}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

case class ServerComponents(
  app: Service,
  server: Server
)

object Server extends IOApp:
  LogConf.init()
  private val log = AppLogger(getClass)
  private val serverPort: Port =
    sys.env.get("SERVER_PORT").flatMap(s => Port.fromString(s)).getOrElse(port"9001")

  def server(
    conf: LogstreamsConf,
    authBuilder: AuthBuilder,
    port: Port = serverPort
  ): Resource[IO, ServerComponents] =
    for
      service <- appService(conf, authBuilder)
      _ <- Resource.eval(
        IO(log.info(s"Binding on port $port using app version ${AppMeta.ThisApp.git}..."))
      )
      server <- EmberServerBuilder
        .default[IO]
        .withIdleTimeout(30.days)
        .withHost(host"0.0.0.0")
        .withPort(serverPort)
        .withHttpWebSocketApp(socketBuilder => makeHandler(service, socketBuilder))
        .withErrorHandler(ErrorHandler[IO].partial)
        .withShutdownTimeout(1.millis)
        .build
    yield ServerComponents(service, server)

  private def appService(conf: LogstreamsConf, authBuilder: AuthBuilder): Resource[IO, Service] =
    for
      http <- HttpClientIO.resource[IO]
      dispatcher <- Dispatcher[IO]
      _ <- Resource.eval(LogstreamsUtils.install(LogConf.name, LogConf.userAgent, dispatcher, http))
      db <- DoobieDatabase.withMigrations(conf.db)
      logsTopic <- Resource.eval(Topic[IO, LogEntryInputs])
      adminsTopic <- Resource.eval(Topic[IO, LogSources])
      connecteds <- Resource.eval(Ref[IO].of(LogSources(Nil)))
      logUpdates <- Resource.eval(Topic[IO, AppLogEvents])
      logsDatabase = DoobieStreamsDatabase(db)
      sockets = LogSockets(logsTopic, adminsTopic, connecteds, logUpdates, logsDatabase)
      _ <- fs2.Stream.emit(()).concurrently(sockets.publisher).compile.resource.lastOrError
    yield
      val users = DoobieDatabaseAuth(db)
      val auths: Auther[IO] = authBuilder(users, Http4sAuth(JWT(conf.secret)))
      val google = GoogleAuthFlow(conf.google, http)
      val isProd = LocalConf.isProd
      Service(
        db,
        users,
        Htmls.forApp("frontend", isProd, AssetsSource(isProd)),
        auths,
        sockets,
        google
      )

  private def makeHandler(service: Service, socketBuilder: WebSocketBuilder2[IO]) = GZip {
    HSTS {
      orNotFound {
        Router(
          "/" -> service.routes(socketBuilder),
          "/assets" -> StaticService[IO].routes
        )
      }
    }
  }

  private def orNotFound(rs: HttpRoutes[IO]): Kleisli[IO, Request[IO], Response[IO]] =
    Kleisli(req => rs.run(req).getOrElseF(BasicService.notFound(req)))

  override def run(args: List[String]): IO[ExitCode] =
    server(LogstreamsConf.parse(), Auths).use(_ => IO.never).as(ExitCode.Success)
