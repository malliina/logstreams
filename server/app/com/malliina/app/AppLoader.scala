package com.malliina.app

import buildinfo.BuildInfo
import com.malliina.logstreams.auth.{Auths, UserService}
import com.malliina.logstreams.db.{DatabaseAuth, DatabaseConf, StreamsDatabase, StreamsSchema}
import com.malliina.logstreams.html.Htmls
import com.malliina.oauth.{GoogleOAuthCredentials, GoogleOAuthReader}
import com.malliina.play.ActorExecution
import com.malliina.play.app.DefaultApp
import controllers._
import play.api.ApplicationLoader.Context
import play.api._
import play.api.routing.Router
import play.filters.HttpFiltersComponents
import play.filters.headers.SecurityHeadersConfig
import play.filters.hosts.AllowedHostsConfig
import router.Routes

import scala.concurrent.Future

class AppLoader extends DefaultApp(new ProdAppComponents(_))

class ProdAppComponents(ctx: Context) extends AppComponents(ctx) {
  override lazy val auth = new WebAuth(authImpl)
}

abstract class AppComponents(context: Context)
  extends BuiltInComponentsFromContext(context)
    with HttpFiltersComponents
    with AssetsComponents {

  def auth: LogAuth

  val mode = environment.mode
  val isProd = environment.mode == Mode.Prod

  val creds =
    if (mode != Mode.Test) GoogleOAuthReader.load
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
  val databaseSchema = StreamsSchema(DatabaseConf(configuration, mode))
  val csp = s"default-src 'self' 'unsafe-inline' ${allowedDomains.mkString(" ")}; connect-src *; img-src 'self' data:;"
  override lazy val securityHeadersConfig: SecurityHeadersConfig =
    SecurityHeadersConfig(contentSecurityPolicy = Option(csp))
  implicit val ec = materializer.executionContext
  val actions = controllerComponents.actionBuilder
  // Services
  val database = StreamsDatabase(databaseSchema)
  val htmls = Htmls.forApp(BuildInfo.frontName, isProd)
  val users: UserService = DatabaseAuth(databaseSchema)
  lazy val listenerAuth = Auths.viewers(auth)
  val sourceAuth = Auths.sources(users)
  val oauth = new OAuth(actions, creds)
  val authImpl = new OAuthCtrl(oauth, materializer)
  val deps = ActorExecution(actorSystem, materializer)

  // Controllers
  lazy val home = new Logs(htmls, auth, users, deps, assets, controllerComponents)
  lazy val sockets = new SocketsBundle(listenerAuth, sourceAuth, database, deps)
  override lazy val router: Router = new Routes(httpErrorHandler, home, sockets, oauth)

  applicationLifecycle.addStopHook(() => Future.successful {
    databaseSchema.close()
  })
}
