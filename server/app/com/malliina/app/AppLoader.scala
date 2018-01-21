package com.malliina.app

import buildinfo.BuildInfo
import com.malliina.logstreams.auth.{Auths, UserService}
import com.malliina.logstreams.db.{DatabaseAuth, UserDB}
import com.malliina.logstreams.html.Htmls
import com.malliina.oauth.{GoogleOAuthCredentials, GoogleOAuthReader}
import com.malliina.play.ActorExecution
import com.malliina.play.app.DefaultApp
import controllers._
import play.api.ApplicationLoader.Context
import play.api._
import play.api.mvc.{ActionBuilder, AnyContent, Request}
import play.api.routing.Router
import play.filters.HttpFiltersComponents
import play.filters.headers.SecurityHeadersConfig
import play.filters.hosts.{AllowedHostsConfig, AllowedHostsFilter}
import router.Routes

import scala.concurrent.Future

class AppLoader extends DefaultApp(new ProdAppComponents(_))

class ProdAppComponents(ctx: Context)
  extends AppComponents(ctx, GoogleOAuthReader.load, mode => UserDB.init(mode != Mode.Prod)) {
  override lazy val auth = new WebAuth(authImpl)
}

abstract class AppComponents(context: Context,
                             creds: GoogleOAuthCredentials,
                             db: Mode => UserDB)
  extends BuiltInComponentsFromContext(context)
    with HttpFiltersComponents
    with AssetsComponents {

  def auth: LogAuth

  lazy val isProd = environment.mode == Mode.Prod
  val allowedHosts = AllowedHostsFilter(AllowedHostsConfig(Seq("localhost", "logs.malliina.com")), httpErrorHandler)

  override def httpFilters = Seq(securityHeadersFilter, allowedHosts)

  val csp = "default-src 'self' 'unsafe-inline' *.bootstrapcdn.com *.googleapis.com; connect-src *"
  override lazy val securityHeadersConfig = SecurityHeadersConfig(contentSecurityPolicy = Option(csp))
  implicit val ec = materializer.executionContext
  val actions: ActionBuilder[Request, AnyContent] = controllerComponents.actionBuilder
  // Services
  lazy val database = db(environment.mode)
  lazy val htmls = Htmls.forApp(BuildInfo.frontName, isProd)
  lazy val users: UserService = DatabaseAuth(database)
  lazy val listenerAuth = Auths.viewers(auth)
  lazy val sourceAuth = Auths.sources(users)
  lazy val oauth = new OAuth(actions, creds, materializer)
  lazy val authImpl = new OAuthCtrl(oauth)
  lazy val deps = ActorExecution(actorSystem, materializer)

  // Controllers
  lazy val home = new Logs(htmls, auth, users, deps, assets, controllerComponents)
  lazy val sockets = new SocketsBundle(listenerAuth, sourceAuth, deps)
  override lazy val router: Router = new Routes(httpErrorHandler, home, sockets, oauth)

  applicationLifecycle.addStopHook(() => Future.successful {
    database.close()
  })
}
