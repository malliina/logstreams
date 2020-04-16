package com.malliina.logstreams.js

import com.malliina.logstreams.models.AppName
import org.scalajs.dom
import play.api.libs.json.Json

trait Settings {
  def isVerbose: Boolean
  def saveVerbose(newVerbose: Boolean): Unit
  def apps: Seq[AppName]
  def saveApps(apps: Seq[AppName]): Unit

  def appendDistinct(app: AppName): Seq[AppName] = {
    val before = apps
    if (before.contains(app)) {
      before
    } else {
      val after = before :+ app
      saveApps(after)
      after
    }
  }

  def remove(app: AppName): Seq[AppName] = {
    val remaining = apps.filter(_ != app)
    saveApps(remaining)
    remaining
  }
}

object StorageSettings extends Settings {
  private val VerboseKey = "verbose"
  private val AppsKey = "apps"

  val localStorage = dom.window.localStorage

  def isVerbose: Boolean =
    Option(localStorage.getItem(VerboseKey)).contains("true")

  def saveVerbose(newVerbose: Boolean): Unit =
    localStorage.setItem(VerboseKey, if (newVerbose) "true" else "false")

  def apps: Seq[AppName] =
    Option(localStorage.getItem(AppsKey)).map(s => Json.parse(s).as[Seq[AppName]]).getOrElse(Nil)

  def saveApps(newApps: Seq[AppName]): Unit =
    localStorage.setItem(AppsKey, Json.stringify(Json.toJson(newApps)))
}
