package com.malliina.logstreams

import cats.effect.IO
import cats.effect.kernel.Async
import cats.effect.std.Dispatcher
import cats.syntax.all.toFlatMapOps
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.{Level, Logger, LoggerContext}
import ch.qos.logback.core.ConsoleAppender
import com.malliina.app.BuildInfo
import com.malliina.http.HttpClient
import com.malliina.http.io.HttpClientF2
import com.malliina.logback.LogbackUtils
import com.malliina.logback.fs2.FS2AppenderComps
import com.malliina.logstreams.client.FS2Appender
import com.malliina.util.AppLogger
import org.slf4j.LoggerFactory

object LogConf:
  val name = "logstreams"
  val userAgent = s"logstreams/${BuildInfo.version} (${BuildInfo.gitHash.take(7)})"

  def init(): Unit =
    val lc = LogbackUtils.init()
    lc.getLogger("org.http4s.ember.server.EmberServerBuilderCompanionPlatform").setLevel(Level.OFF)
    lc.getLogger("com.malliina.logstreams.http4s.LogSockets").setLevel(Level.WARN)
