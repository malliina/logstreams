package com.malliina.app

import controllers.AkkaStreamCtrl
import controllers.{Assets, Logs}
import play.api.routing.Router
import play.api._
import play.api.ApplicationLoader.Context
import router.Routes

class AppLoader extends LoggingAppLoader[AppComponents] with WithAppComponents

class AppComponents(context: Context) extends BuiltInComponentsFromContext(context) {
  lazy val assets = new Assets(httpErrorHandler)
  // Controllers
  val home = new Logs()(actorSystem, materializer)
  val as = new AkkaStreamCtrl()(materializer)
  override val router: Router = new Routes(httpErrorHandler, home, as, assets)
}
