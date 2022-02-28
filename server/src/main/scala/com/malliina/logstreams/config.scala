package com.malliina.logstreams

import com.malliina.app.BuildInfo
import com.malliina.http.FullUrl
import com.malliina.logstreams.auth.SecretKey
import com.malliina.logstreams.db.Conf
import com.malliina.values.{ErrorMessage, Readable}
import com.malliina.web.{AuthConf, ClientId, ClientSecret}
import com.typesafe.config.{Config, ConfigFactory}

import java.nio.file.Paths

sealed trait AppMode:
  def isProd = this == AppMode.Prod

object AppMode:
  case object Prod extends AppMode
  case object Dev extends AppMode

  implicit val reader: ConfigReadable[AppMode] = ConfigReadable.string.emap {
    case "prod" => Right(Prod)
    case "dev"  => Right(Dev)
    case other  => Left(ErrorMessage(s"Invalid mode: '$other'. Mode must be 'prod' or 'dev'."))
  }

object LocalConf:
  val homeDir = Paths.get(sys.props("user.home"))
  val appDir = LocalConf.homeDir.resolve(".logstreams")
  val localConfFile = appDir.resolve("logstreams.conf")
  val isProd = BuildInfo.mode == "prod"
  private val localConf =
    ConfigFactory.parseFile(localConfFile.toFile).withFallback(ConfigFactory.load())
  val conf: Config =
    if isProd then ConfigFactory.load("application-prod.conf")
    else localConf

case class LogstreamsConf(secret: SecretKey, db: Conf, google: AuthConf)

case class WrappedConf(logstreams: LogstreamsConf)

trait ConfigReadable[T]:
  def read(key: String, c: Config): Either[ErrorMessage, T]
  def flatMap[U](f: T => ConfigReadable[U]): ConfigReadable[U] =
    val parent = this
    (key: String, c: Config) => parent.read(key, c).flatMap(t => f(t).read(key, c))
  def emap[U](f: T => Either[ErrorMessage, U]): ConfigReadable[U] = (key: String, c: Config) =>
    read(key, c).flatMap(f)
  def map[U](f: T => U): ConfigReadable[U] = emap(t => Right(f(t)))

object ConfigReadable:
  implicit val string: ConfigReadable[String] = (key: String, c: Config) => Right(c.getString(key))
  implicit val url: ConfigReadable[FullUrl] = string.emap(s => FullUrl.build(s))
  implicit val int: ConfigReadable[Int] = (key: String, c: Config) => Right(c.getInt(key))
  implicit val bool: ConfigReadable[Boolean] = (key: String, c: Config) => Right(c.getBoolean(key))
  implicit val config: ConfigReadable[Config] = (key: String, c: Config) => Right(c.getConfig(key))

  implicit def readable[T](implicit r: Readable[T]): ConfigReadable[T] =
    string.emap(s => r.read(s))

object LogstreamsConf:
  implicit class ConfigOps(c: Config):
    def read[T](key: String)(implicit r: ConfigReadable[T]): Either[ErrorMessage, T] =
      r.read(key, c)
    def unsafe[T: ConfigReadable](key: String): T =
      c.read[T](key).fold(err => throw new IllegalArgumentException(err.message), identity)

  def parse(): LogstreamsConf =
    val c = ConfigFactory.load(LocalConf.conf).resolve().getConfig("logstreams")
    val db = c.getConfig("db")
    val google = c.getConfig("google")
    LogstreamsConf(
      c.unsafe[SecretKey]("secret"),
      parseDatabase(db),
      AuthConf(google.unsafe[ClientId]("client-id"), google.unsafe[ClientSecret]("client-secret"))
    )

  def parseDatabase(from: Config) = Conf(
    from.unsafe[String]("url"),
    from.unsafe[String]("user"),
    from.unsafe[String]("pass"),
    from.unsafe[String]("driver")
  )
