package com.malliina.logstreams

import ch.qos.logback.classic.Level
import com.malliina.app.BuildInfo
import com.malliina.logback.LogbackUtils

object LogConf:
  val name = "logstreams"
  val userAgent = s"logstreams/${BuildInfo.version} (${BuildInfo.gitHash.take(7)})"

  def init(): Unit =
    val customLevels = Map(
      "org.http4s.ember.server.EmberServerBuilderCompanionPlatform" -> Level.OFF,
      "com.malliina.logstreams.http4s.LogSockets" -> Level.INFO
    )
    LogbackUtils.init(levelsByLogger = customLevels)
