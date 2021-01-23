package com.malliina.logstreams

import com.malliina.logstreams.auth.SecretKey

import java.nio.file.Paths
import com.malliina.logstreams.db.Conf
import com.typesafe.config.ConfigFactory
import pureconfig.{ConfigObjectSource, ConfigSource}

object LocalConf {
  val homeDir = Paths.get(sys.props("user.home"))
  val appDir = LocalConf.homeDir.resolve(".logstreams")
  val localConfFile = appDir.resolve("logstreams.conf")
  val localConf = ConfigFactory.parseFile(localConfFile.toFile)
}

case class LogstreamsConf(db: Conf, jwt: SecretKey)

case class WrappedConf(logstreams: LogstreamsConf)

object LogstreamsConf {
  import pureconfig.generic.auto.exportReader
  val load: LogstreamsConf = ConfigObjectSource(Right(LocalConf.localConf))
    .withFallback(ConfigSource.default)
    .loadOrThrow[WrappedConf]
    .logstreams
}
