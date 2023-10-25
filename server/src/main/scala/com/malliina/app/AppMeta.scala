package com.malliina.app

import io.circe.Codec

case class AppMeta(name: String, version: String, git: String) derives Codec.AsObject

object AppMeta:
  val ThisApp = AppMeta(BuildInfo.name, BuildInfo.version, BuildInfo.gitHash)
