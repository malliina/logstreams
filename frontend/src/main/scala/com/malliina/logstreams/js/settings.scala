package com.malliina.logstreams.js

import com.malliina.logstreams.models.{AppName, LogLevel}
import io.circe.parser.decode
import io.circe.syntax.EncoderOps
import org.scalajs.dom

trait Settings:
  def isVerbose: Boolean
  def saveVerbose(newVerbose: Boolean): Unit
  def apps: Seq[AppName]
  def saveApps(apps: Seq[AppName]): Unit
  def level: LogLevel
  def saveLevel(newLevel: LogLevel): Unit
  def query: Option[String]
  def saveQuery(newQuery: Option[String]): Unit

  def appendDistinct(app: AppName): Seq[AppName] =
    val before = apps
    if before.contains(app) then before
    else
      val after = before :+ app
      saveApps(after)
      after

  def remove(app: AppName): Seq[AppName] =
    val remaining = apps.filter(_ != app)
    saveApps(remaining)
    remaining

object StorageSettings extends Settings:
  private val VerboseKey = "verbose"
  private val AppsKey = "apps"
  private val LevelKey = "level"
  private val QueryKey = "query"

  val localStorage = dom.window.localStorage

  def isVerbose: Boolean =
    Option(localStorage.getItem(VerboseKey)).contains("true")
  def saveVerbose(newVerbose: Boolean): Unit =
    localStorage.setItem(VerboseKey, if newVerbose then "true" else "false")

  def apps: Seq[AppName] =
    Option(localStorage.getItem(AppsKey))
      .flatMap(s => decode[Seq[AppName]](s).toOption)
      .getOrElse(Nil)
  def saveApps(newApps: Seq[AppName]): Unit =
    localStorage.setItem(AppsKey, newApps.asJson.noSpaces)

  def level: LogLevel = Option(localStorage.getItem(LevelKey))
    .flatMap(s => LogLevel.build(s).toOption)
    .getOrElse(LogLevel.Info)
  def saveLevel(newLevel: LogLevel): Unit =
    localStorage.setItem(LevelKey, newLevel.name)

  def query: Option[String] =
    Option(localStorage.getItem(QueryKey)).filter(_.length >= 3)
  def saveQuery(newQuery: Option[String]): Unit =
    localStorage.setItem(QueryKey, newQuery.getOrElse(""))
