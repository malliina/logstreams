package com.malliina.app

import java.nio.file.Paths

import com.malliina.logstreams.db._
import com.typesafe.config.ConfigFactory
import play.api._

object LocalConf {
  val homeDir = Paths.get(sys.props("user.home"))
  val appDir = LocalConf.homeDir.resolve(".logstreams")
  val localConfFile = appDir.resolve("logstreams.conf")
  val localConf = ConfigFactory.parseFile(localConfFile.toFile)
}

trait AppConf {
  def database: Conf
  def close(): Unit
}

object AppConf {
  def apply(conf: Conf) = new AppConf {
    override def database: Conf = conf
    override def close(): Unit = ()
  }
}

//class AppLoader extends ApplicationLoader {
//  override def load(context: Context): Application = {
//    val environment = context.environment
//    LoggerConfigurator(environment.classLoader)
//      .foreach(_.configure(environment))
//    new ProdAppComponents(context).application
//  }
//}

//class ProdAppComponents(ctx: Context)
//  extends AppComponents(
//    ctx,
//    c => Conf.fromConf(c).map(c => AppConf(c)).toOption.get
//  ) {
//  override lazy val auth = new WebAuth(authImpl)
//}
//
//abstract class AppComponents(context: Context, dbConf: Configuration => AppConf)
//  extends BuiltInComponentsFromContext(context)
//  with HttpFiltersComponents
//  with AssetsComponents {
//
//  def auth: LogAuth
//
//  val mode = environment.mode
//  val isProd = environment.mode == Mode.Prod
//  override val configuration = LocalConf.localConf.withFallback(context.initialConfiguration)
//  override lazy val httpFilters: Seq[EssentialFilter] =
//    Seq(new GzipFilter(), csrfFilter, securityHeadersFilter)
//  val creds: GoogleOAuthCredentials =
//    if (mode != Mode.Test)
//      GoogleOAuthCredentials(configuration).fold(err => throw new Exception(err.message), identity)
//    else
//      GoogleOAuthCredentials("", "", "")
////  override lazy val allowedHostsConfig: AllowedHostsConfig =
////    AllowedHostsConfig(Seq("localhost", "logs.malliina.com"))
//  val allowedDomains = Seq(
//    "*.bootstrapcdn.com",
//    "*.googleapis.com",
//    "cdnjs.cloudflare.com",
//    "code.jquery.com",
//    "use.fontawesome.com"
//  )
//  val csp =
//    s"default-src 'self' 'unsafe-inline' 'unsafe-eval' ${allowedDomains.mkString(" ")}; connect-src *; img-src 'self' data:;"
//  override lazy val securityHeadersConfig: SecurityHeadersConfig =
//    SecurityHeadersConfig(contentSecurityPolicy = Option(csp))
//  val defaultHttpConf = HttpConfiguration.fromConfiguration(configuration, environment)
//  // Sets sameSite = None, otherwise the Google auth redirect will wipe out the session state
//  override lazy val httpConfiguration: HttpConfiguration =
//    defaultHttpConf.copy(
//      session = defaultHttpConf.session.copy(cookieName = "logsSession", sameSite = None)
//    )
//
//  implicit val ec: ExecutionContextExecutor = materializer.executionContext
//  val actions = controllerComponents.actionBuilder
//  // Services
//  val appConf = dbConf(configuration)
//  val doobieDb = DoobieDatabase.withMigrations(appConf.database, executionContext)
//  val db = DoobieStreamsDatabase(doobieDb)
//  val database: LogsDatabase = db
//  val htmls = Htmls.forApp(BuildInfo.frontName, isProd)
//  val usersDb = DoobieDatabaseAuth(doobieDb)
//  val users: UserService = usersDb
//  // if non-lazy, NPEs here due to initialization. fix later.
//  lazy val listenerAuth = Auths.viewers(auth)
//  val sourceAuth = Auths.sources(users)
//  val oauth = new OAuth(actions, creds)
//  val authImpl = new OAuthCtrl(oauth, materializer)
//  val deps = ActorExecution(actorSystem, materializer)
//
//  // Controllers
//  val home = new Logs(htmls, auth, users, deps, assets, controllerComponents)
//  val sockets = new SocketsBundle(listenerAuth, sourceAuth, database, deps)
//  override val router: Router = new Routes(httpErrorHandler, home, sockets, oauth)
//
//  applicationLifecycle.addStopHook(() =>
//    Future.successful {
//      doobieDb.close()
//      appConf.close()
//    }
//  )
//}
