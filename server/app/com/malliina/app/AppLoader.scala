package com.malliina.app

import buildinfo.BuildInfo
import com.malliina.logstreams.auth.{Auths, UserService}
import com.malliina.logstreams.db.{DatabaseAuth, UserDB}
import com.malliina.logstreams.tags.Htmls
import com.malliina.oauth.{GoogleOAuthCredentials, GoogleOAuthReader}
import com.malliina.play.ActorExecution
import com.malliina.play.app.DefaultApp
import controllers._
import play.api.ApplicationLoader.Context
import play.api._
import play.api.mvc.{ActionBuilder, AnyContent, Request}
import play.api.routing.Router
import play.filters.HttpFiltersComponents
import router.Routes

import scala.concurrent.{ExecutionContext, Future}

class AppLoader extends DefaultApp(new ProdAppComponents(_))

class ProdAppComponents(ctx: Context)
  extends AppComponents(ctx, GoogleOAuthReader.load, ec => UserDB.default()(ec)) {
  override lazy val auth = new WebAuth(authImpl)
}

abstract class AppComponents(context: Context,
                             creds: GoogleOAuthCredentials,
                             db: ExecutionContext => UserDB)
  extends BuiltInComponentsFromContext(context)
    with HttpFiltersComponents
    with AssetsComponents {

  implicit val ec = materializer.executionContext

  def auth: LogAuth

  val actions: ActionBuilder[Request, AnyContent] = controllerComponents.actionBuilder
  // Services
  lazy val isProd = environment.mode == Mode.Prod
  lazy val htmls = Htmls.forApp(BuildInfo.frontName, isProd)
  val database = db(ec)
  lazy val users: UserService = DatabaseAuth(database)
  lazy val listenerAuth = Auths.viewers(auth)
  lazy val sourceAuth = Auths.sources(users)
  lazy val oauth = new OAuth(actions, creds, materializer)
  lazy val authImpl = new OAuthCtrl(oauth)
  lazy val deps = ActorExecution(actorSystem, materializer)

  // Controllers
  lazy val home = new Logs(htmls, auth, users, deps, controllerComponents)
  lazy val sockets = new SocketsBundle(listenerAuth, sourceAuth, deps)
  override lazy val router: Router = new Routes(httpErrorHandler, home, sockets, oauth, assets)

  applicationLifecycle.addStopHook(() => Future.successful {
    database.close()
  })
}
