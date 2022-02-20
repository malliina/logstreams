package com.malliina.app

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class AppMeta(name: String, version: String, git: String)

object AppMeta:
  implicit val json: Codec[AppMeta] = deriveCodec[AppMeta]

  val ThisApp = AppMeta(BuildInfo.name, BuildInfo.version, BuildInfo.gitHash)
