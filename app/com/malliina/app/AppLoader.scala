package com.malliina.app

import com.malliina.logstreams.auth.PassThruAuth
import com.malliina.logstreams.tags.Htmls
import com.malliina.play.app.DefaultApp
import controllers.OAuth
import controllers.OAuthCtrl
import controllers.{Assets, Logs}
import play.api.ApplicationLoader.Context
import play.api._
import play.api.routing.Router
import router.Routes

class AppLoader extends DefaultApp(new AppComponents(_))

class AppComponents(context: Context) extends BuiltInComponentsFromContext(context) {
  lazy val assets = new Assets(httpErrorHandler)
  lazy val isProd = environment.mode == Mode.Prod
  lazy val htmls = Htmls.forApp("frontend", isProd)
  lazy val oauth = new OAuthCtrl(new OAuth(materializer))
  lazy val users = new PassThruAuth
  // Controllers
  val home = new Logs(htmls, oauth, users)(actorSystem, materializer)
  override val router: Router = new Routes(httpErrorHandler, home, oauth, assets)
}
