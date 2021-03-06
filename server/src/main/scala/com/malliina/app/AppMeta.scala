package com.malliina.app

import play.api.libs.json.Json

case class AppMeta(name: String, version: String, git: String)

object AppMeta {
  implicit val json = Json.format[AppMeta]

  val ThisApp = AppMeta(BuildInfo.name, BuildInfo.version, BuildInfo.hash)
}
