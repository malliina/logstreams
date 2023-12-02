package com.malliina.logstreams

import com.malliina.app.BuildInfo
import com.malliina.config.{ConfigError, ConfigNode, Env}
import com.malliina.database.Conf
import com.malliina.logstreams.auth.SecretKey
import com.malliina.values.{Password, Readable}
import com.malliina.web.{AuthConf, ClientId, ClientSecret}

import java.nio.file.Paths

object LocalConf:
  private val homeDir = Paths.get(sys.props("user.home"))
  private val appDir = LocalConf.homeDir.resolve(".logstreams")
  private val localConfFile = appDir.resolve("logstreams.conf")
  val conf: ConfigNode =
    if BuildInfo.isProd then ConfigNode.load("application-prod.conf")
    else ConfigNode.default(localConfFile)

case class LogstreamsConf(secret: SecretKey, db: Conf, google: AuthConf)

object LogstreamsConf:
  private val googleClientId = ClientId(
    "122390040180-qflbc31ourl9gfinfl7jhl0jk70i8jur.apps.googleusercontent.com"
  )

  private def logsConf = LocalConf.conf.parse[ConfigNode]("logstreams").toOption.get

  def parseUnsafe() = parse().fold(err => throw err, identity)

  def parse(c: ConfigNode = logsConf): Either[ConfigError, LogstreamsConf] =
    val env = Env.read[String]("ENV_NAME")
    val isStaging = env.contains("staging")
    val isProd = env.contains("prod")
    for
      secret <-
        if isProd then c.parse[SecretKey]("secret")
        else c.opt[SecretKey]("secret").map(_.getOrElse(SecretKey.dev))
      dbPass <- c.parse[Password]("db.pass")
      googleSecret <- c.parse[ClientSecret]("google.client-secret")
    yield LogstreamsConf(
      secret,
      if BuildInfo.isProd then prodDatabaseConf(dbPass, if isStaging then 2 else 5)
      else devDatabaseConf(dbPass),
      AuthConf(googleClientId, googleSecret)
    )

  private def prodDatabaseConf(password: Password, maxPoolSize: Int) = Conf(
    "jdbc:mysql://database8-nuqmhn2cxlhle.mysql.database.azure.com:3306/logstreams",
    "logstreams",
    password.pass,
    Conf.MySQLDriver,
    maxPoolSize,
    autoMigrate = true
  )

  private def devDatabaseConf(password: Password) = Conf(
    "jdbc:mysql://localhost:3307/logstreams",
    "logstreams",
    password.pass,
    Conf.MySQLDriver,
    maxPoolSize = 2,
    autoMigrate = false
  )
