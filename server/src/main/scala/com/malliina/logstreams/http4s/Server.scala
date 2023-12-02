package com.malliina.logstreams.http4s

import cats.data.Kleisli
import cats.effect.kernel.Ref
import cats.effect.std.Dispatcher
import cats.effect.{Async, ExitCode, IO, IOApp, Resource}
import com.comcast.ip4s.{Port, host, port}
import com.malliina.app.{AppMeta, BuildInfo}
import com.malliina.database.DoobieDatabase
import com.malliina.http.io.HttpClientIO
import com.malliina.logstreams.auth.{AuthBuilder, Auther, Auths, JWT}
import com.malliina.logstreams.client.LogstreamsUtils
import com.malliina.logstreams.db.{DoobieDatabaseAuth, DoobieLogsDatabase}
import com.malliina.logstreams.html.{AssetsSource, Htmls}
import com.malliina.logstreams.models.{AppLogEvents, LogEntryInputs, LogSources}
import com.malliina.logstreams.{LocalConf, LogConf, LogstreamsConf}
import com.malliina.util.AppLogger
import com.malliina.web.GoogleAuthFlow
import fs2.concurrent.Topic
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.{GZip, HSTS}
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.server.{Router, Server}
import org.http4s.{HttpRoutes, Request, Response}

import scala.concurrent.duration.{Duration, DurationInt}

case class ServerComponents[F[_]: Async](
  app: Service[F],
  server: Server
)

object Server extends IOApp:
  override def runtimeConfig =
    super.runtimeConfig.copy(cpuStarvationCheckInitialDelay = Duration.Inf)
  LogConf.init()
  private val log = AppLogger(getClass)
  private val serverPort: Port =
    sys.env.get("SERVER_PORT").flatMap(s => Port.fromString(s)).getOrElse(port"9001")

  def server[F[_]: Async](
    conf: LogstreamsConf,
    authBuilder: AuthBuilder,
    port: Port = serverPort
  ): Resource[F, ServerComponents[F]] =
    for
      service <- appService[F](conf, authBuilder)
      _ <- Resource.eval(
        Async[F].delay(
          log.info(s"Binding on port $port using app version ${AppMeta.ThisApp.git}...")
        )
      )
      server <- EmberServerBuilder
        .default[F]
        .withIdleTimeout(30.days)
        .withHost(host"0.0.0.0")
        .withPort(serverPort)
        .withHttpWebSocketApp(socketBuilder => makeHandler(service, socketBuilder))
        .withErrorHandler(ErrorHandler[F].partial)
        .withShutdownTimeout(1.millis)
        .build
    yield ServerComponents(service, server)

  private def appService[F[_]: Async](
    conf: LogstreamsConf,
    authBuilder: AuthBuilder
  ): Resource[F, Service[F]] =
    for
      http <- HttpClientIO.resource[F]
      dispatcher <- Dispatcher.parallel[F]
      _ <- Resource.eval(
        LogstreamsUtils.installIfEnabled(LogConf.name, LogConf.userAgent, dispatcher, http)
      )
      db <- DoobieDatabase.init[F](conf.db)
      logsTopic <- Resource.eval(Topic[F, LogEntryInputs])
      adminsTopic <- Resource.eval(Topic[F, LogSources])
      connecteds <- Resource.eval(Ref[F].of(LogSources(Nil)))
      logUpdates <- Resource.eval(Topic[F, AppLogEvents])
      logsDatabase = DoobieLogsDatabase(db)
      sockets = LogSockets(logsTopic, adminsTopic, connecteds, logUpdates, logsDatabase)
      _ <- fs2.Stream.emit(()).concurrently(sockets.publisher).compile.resource.lastOrError
    yield
      val users = DoobieDatabaseAuth(db)
      val auths: Auther[F] = authBuilder(users, Http4sAuth(JWT(conf.secret)))
      val google = GoogleAuthFlow(conf.google, http)
      val isProd = BuildInfo.isProd
      Service(
        db,
        users,
        Htmls.forApp("frontend", isProd, AssetsSource(isProd)),
        auths,
        sockets,
        google
      )

  private def makeHandler[F[_]: Async](service: Service[F], socketBuilder: WebSocketBuilder2[F]) =
    GZip:
      HSTS:
        orNotFound:
          Router(
            "/" -> service.routes(socketBuilder),
            "/assets" -> StaticService[F].routes
          )

  private def orNotFound[F[_]: Async](
    rs: HttpRoutes[F]
  ): Kleisli[F, Request[F], Response[F]] =
    Kleisli(req => rs.run(req).getOrElseF(BasicService[F].notFound(req)))

  override def run(args: List[String]): IO[ExitCode] =
    server[IO](LogstreamsConf.parseUnsafe(), Auths).use(_ => IO.never).as(ExitCode.Success)
