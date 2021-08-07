package com.malliina.app

import io.circe.Codec

case class AppMeta(name: String, version: String, git: String)

object AppMeta {
  implicit val json: Codec[AppMeta] = ???

  val ThisApp = AppMeta(BuildInfo.name, BuildInfo.version, BuildInfo.hash)
}
