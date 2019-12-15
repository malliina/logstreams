package com.malliina.app

import java.nio.file.{Files, Path}

import ch.vorburger.mariadb4j.{DB, DBConfiguration, DBConfigurationBuilder}
import com.malliina.logstreams.db.Conf

object EmbeddedMySQL {
  def temporary: EmbeddedMySQL =
    apply(Files.createTempDirectory("embedded"), temporary = true)
  lazy val permanent: EmbeddedMySQL =
    apply(LocalConf.appDir.resolve("embedded"), temporary = false)

  def apply(baseDir: Path, temporary: Boolean): EmbeddedMySQL =
    new EmbeddedMySQL(baseDir, temporary)
}

class EmbeddedMySQL(baseDir: Path, temporary: Boolean) {
  private val dbConfig: DBConfiguration =
    DBConfigurationBuilder
      .newBuilder()
      .setDataDir(baseDir.resolve("data").toAbsolutePath.toString)
      .setLibDir(baseDir.resolve("lib").toAbsolutePath.toString)
      .setDeletingTemporaryBaseAndDataDirsOnShutdown(temporary)
      .build()
  lazy val db = DB.newEmbeddedDB(dbConfig)
  lazy val conf: Conf = {
    db.start()
    val dbName = "pimptest"
    db.createDB(dbName)
    Conf(dbConfig.getURL(dbName), "root", "", Conf.MySQLDriver)
  }

  def stop(): Unit = db.stop()
}
