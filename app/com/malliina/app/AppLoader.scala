package com.malliina.app

import com.malliina.logstreams.tags.Htmls
import com.malliina.play.app.DefaultApp
import controllers.{AkkaStreamCtrl, Assets, Logs}
import play.api.ApplicationLoader.Context
import play.api._
import play.api.routing.Router
import router.Routes

class AppLoader extends DefaultApp(new AppComponents(_))

class AppComponents(context: Context) extends BuiltInComponentsFromContext(context) {
  lazy val assets = new Assets(httpErrorHandler)
  lazy val isProd = environment.mode == Mode.Prod
  lazy val htmls = Htmls.forApp("frontend", isProd)
  // Controllers
  val home = new Logs(htmls)(actorSystem, materializer)
  val as = new AkkaStreamCtrl(htmls)(materializer)
  override val router: Router = new Routes(httpErrorHandler, home, as, assets)
}
