package com.malliina.app

import java.nio.file.Paths

import com.malliina.logstreams.auth.{Auths, UserService}
import com.malliina.logstreams.db.NewStreamsDatabase.fail
import com.malliina.logstreams.db._
import com.malliina.logstreams.html.Htmls
import com.malliina.oauth.GoogleOAuthCredentials
import com.malliina.play.ActorExecution
import com.malliina.play.app.DefaultApp
import com.typesafe.config.ConfigFactory
import controllers._
import play.api.ApplicationLoader.Context
import play.api._
import play.api.http.HttpConfiguration
import play.api.routing.Router
import play.filters.HttpFiltersComponents
import play.filters.gzip.GzipFilter
import play.filters.headers.SecurityHeadersConfig
import play.filters.hosts.AllowedHostsConfig
import router.Routes

import scala.concurrent.{ExecutionContextExecutor, Future}

object LocalConf {
  val localConfFile = Paths.get(sys.props("user.home")).resolve(".logstreams/logstreams.conf")
  val localConf = Configuration(ConfigFactory.parseFile(localConfFile.toFile))
}

class AppLoader extends DefaultApp(new ProdAppComponents(_))

class ProdAppComponents(ctx: Context)
    extends AppComponents(ctx, c => Conf.fromConf(c).fold(fail, identity)) {
  override lazy val auth = new WebAuth(authImpl)
}

abstract class AppComponents(context: Context, dbConf: Configuration => Conf)
    extends BuiltInComponentsFromContext(context)
    with HttpFiltersComponents
    with AssetsComponents {

  def auth: LogAuth

  val mode = environment.mode
  val isProd = environment.mode == Mode.Prod

  override val configuration = context.initialConfiguration ++ LocalConf.localConf
  override lazy val httpFilters =
    Seq(new GzipFilter(), csrfFilter, securityHeadersFilter, allowedHostsFilter)
  val creds: GoogleOAuthCredentials =
    if (mode != Mode.Test)
      GoogleOAuthCredentials(configuration).fold(err => throw new Exception(err.message), identity)
    else GoogleOAuthCredentials("", "", "")
  override lazy val allowedHostsConfig: AllowedHostsConfig =
    AllowedHostsConfig(Seq("localhost", "logs.malliina.com"))
  val allowedDomains = Seq(
    "*.bootstrapcdn.com",
    "*.googleapis.com",
    "cdnjs.cloudflare.com",
    "code.jquery.com",
    "use.fontawesome.com"
  )
  val csp =
    s"default-src 'self' 'unsafe-inline' 'unsafe-eval' ${allowedDomains.mkString(" ")}; connect-src *; img-src 'self' data:;"
  override lazy val securityHeadersConfig: SecurityHeadersConfig =
    SecurityHeadersConfig(contentSecurityPolicy = Option(csp))
  val defaultHttpConf = HttpConfiguration.fromConfiguration(configuration, environment)
  // Sets sameSite = None, otherwise the Google auth redirect will wipe out the session state
  override lazy val httpConfiguration =
    defaultHttpConf.copy(
      session = defaultHttpConf.session.copy(cookieName = "logsSession", sameSite = None)
    )

  implicit val ec: ExecutionContextExecutor = materializer.executionContext
  val actions = controllerComponents.actionBuilder
  // Services
  val db = NewStreamsDatabase.withMigrations(actorSystem, dbConf(configuration))
  val database: LogsDatabase = db
  val htmls = Htmls.forApp(BuildInfo.frontName, isProd)
  val users: UserService = NewDatabaseAuth(db.ds, db.ec)
  lazy val listenerAuth = Auths.viewers(auth)
  val sourceAuth = Auths.sources(users)
  val oauth = new OAuth(actions, creds)
  val authImpl = new OAuthCtrl(oauth, materializer)
  val deps = ActorExecution(actorSystem, materializer)

  // Controllers
  lazy val home = new Logs(htmls, auth, users, deps, assets, controllerComponents)
  lazy val sockets = new SocketsBundle(listenerAuth, sourceAuth, database, deps)
  override lazy val router: Router = new Routes(httpErrorHandler, home, sockets, oauth)

  applicationLifecycle.addStopHook(
    () =>
      Future.successful {
        db.close()
      }
  )
}
