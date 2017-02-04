package com.malliina.app

import buildinfo.BuildInfo
import com.malliina.logstreams.auth.PassThruAuth
import com.malliina.logstreams.tags.Htmls
import com.malliina.play.app.DefaultApp
import controllers._
import play.api.ApplicationLoader.Context
import play.api._
import play.api.routing.Router
import router.Routes

class AppLoader extends DefaultApp(new ProdAppComponents(_))

abstract class AppComponents(context: Context) extends BuiltInComponentsFromContext(context) {
  lazy val assets = new Assets(httpErrorHandler)
  lazy val isProd = environment.mode == Mode.Prod
  lazy val htmls = Htmls.forApp(BuildInfo.frontName, isProd)
  lazy val users = new PassThruAuth

  // mocked for tests
  def auth: LogAuth

  def oauth: OAuthRoutes

  // Controllers
  lazy val home = new Logs(htmls, auth, users)(actorSystem, materializer)
  override lazy val router: Router = new Routes(httpErrorHandler, home, oauth, assets)
}

class ProdAppComponents(ctx: Context) extends AppComponents(ctx) {
  val authImpl = new OAuthCtrl(new OAuth(materializer))
  override lazy val oauth = new WebOAuthRoutes(authImpl)
  override lazy val auth = new WebAuth(authImpl)
}
