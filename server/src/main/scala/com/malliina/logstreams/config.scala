package com.malliina.logstreams

import com.malliina.logstreams.auth.SecretKey
import com.malliina.logstreams.db.Conf
import com.malliina.web.AuthConf
import com.typesafe.config.ConfigFactory
import pureconfig.error.CannotConvert
import pureconfig.{ConfigObjectSource, ConfigReader, ConfigSource}

import java.nio.file.Paths

sealed trait AppMode {
  def isProd = this == AppMode.Prod
}

object AppMode {
  case object Prod extends AppMode
  case object Dev extends AppMode

  implicit val reader: ConfigReader[AppMode] = ConfigReader.stringConfigReader.emap {
    case "prod" => Right(Prod)
    case "dev"  => Right(Dev)
    case other  => Left(CannotConvert(other, "AppMode", "Must be 'prod' or 'dev'."))
  }
}

object LocalConf {
  val homeDir = Paths.get(sys.props("user.home"))
  val appDir = LocalConf.homeDir.resolve(".logstreams")
  val localConfFile = appDir.resolve("logstreams.conf")
  val localConf = ConfigFactory.parseFile(localConfFile.toFile)
}

case class LogstreamsConf(mode: AppMode, jwt: SecretKey, db: Conf, google: AuthConf)

object LogstreamsConf {
  import pureconfig.generic.auto.exportReader
  val load: LogstreamsConf = ConfigObjectSource(Right(LocalConf.localConf))
    .withFallback(ConfigSource.default)
    .loadOrThrow[LogstreamsConf]
}
