package com.malliina.app

import buildinfo.BuildInfo
import com.malliina.logstreams.auth.UserService
import com.malliina.logstreams.db.{DatabaseAuth, UserDB}
import com.malliina.logstreams.tags.Htmls
import com.malliina.play.app.DefaultApp
import controllers._
import play.api.ApplicationLoader.Context
import play.api._
import play.api.routing.Router
import router.Routes

import scala.concurrent.Future

class AppLoader extends DefaultApp(new ProdAppComponents(_))

abstract class AppComponents(context: Context) extends BuiltInComponentsFromContext(context) {
  // Mocked during tests
  def db: UserDB

  def auth: LogAuth

  def oauth: OAuthRoutes

  // Services
  lazy val assets = new Assets(httpErrorHandler)
  lazy val isProd = environment.mode == Mode.Prod
  lazy val htmls = Htmls.forApp(BuildInfo.frontName, isProd)
  lazy val users: UserService = DatabaseAuth(db)
  // Controllers
  lazy val home = new Logs(htmls, auth, users, actorSystem, materializer)
  override lazy val router: Router = new Routes(httpErrorHandler, home, oauth, assets)

  applicationLifecycle.addStopHook(() => Future.successful {
    db.close()
  })
}

class ProdAppComponents(ctx: Context) extends AppComponents(ctx) {
  override val db = UserDB.default()
  val authImpl = new OAuthCtrl(new OAuth(materializer))
  override lazy val oauth = new WebOAuthRoutes(authImpl)
  override lazy val auth = new WebAuth(authImpl)
}
