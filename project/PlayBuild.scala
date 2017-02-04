import com.malliina.sbtplay.PlayProject
import com.malliina.sbtutils.SbtUtils._
import com.typesafe.sbt.web.Import.{Assets, pipelineStages}
import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.AutoImport.persistLauncher
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import play.sbt.PlayImport
import sbt.Keys._
import sbt._
import sbtbuildinfo.BuildInfoKeys.buildInfoKeys
import sbtbuildinfo.{BuildInfoKey, BuildInfoPlugin}
import webscalajs.ScalaJSWeb
import webscalajs.WebScalaJS.autoImport.{scalaJSPipeline, scalaJSProjects}

object PlayBuild {
  lazy val root = Project("root", file("."))
    .settings(basicSettings: _*)
    .aggregate(frontend, server, client, integrationTest)

  lazy val frontend = Project("frontend", file("frontend"))
    .settings(frontSettings: _*)
    .enablePlugins(ScalaJSPlugin, ScalaJSWeb)

  lazy val server = PlayProject.default("logstreams", file("server"))
    .enablePlugins(BuildInfoPlugin)
    .settings(serverSettings: _*)

  lazy val client = Project("logstreams-client", file("client"))
    .settings(clientSettings: _*)

  lazy val integrationTest = Project("logstreams-test", file("logstreams-test"))
    .settings(testSettings: _*)
    .dependsOn(server % "test->test", client)

  val malliinaGroup = "com.malliina"
  val utilPlayDep = malliinaGroup %% "util-play" % "3.5.2"

  def frontSettings = Seq(
    persistLauncher := true,
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "scalatags" % "0.6.2",
      "com.lihaoyi" %%% "upickle" % "0.4.3",
      "be.doeraene" %%% "scalajs-jquery" % "0.9.1"
    )
  )

  def serverSettings = basicSettings ++ scalaJSSettings ++ Seq(
    buildInfoKeys += BuildInfoKey("frontName" -> (name in frontend).value),
    libraryDependencies ++= Seq(
      utilPlayDep,
      utilPlayDep % Test classifier "tests"
    ) map (_.withSources().withJavadoc())
  )

  def scalaJSSettings = Seq(
    scalaJSProjects := Seq(frontend),
    pipelineStages in Assets := Seq(scalaJSPipeline)
  )

  def clientSettings = basicSettings ++ mavenSettings ++ Seq(
    gitUserName := "malliina",
    developerName := "Michael Skogberg",
    libraryDependencies ++= loggingDeps ++ Seq(
      "com.neovisionaries" % "nv-websocket-client" % "1.31",
      PlayImport.json,
      "org.scalatest" %% "scalatest" % "3.0.0" % Test
    )
  )

  def testSettings = basicSettings ++ Seq(
    libraryDependencies += PlayImport.ws
  )

  def basicSettings = Seq(
    organization := malliinaGroup,
    version := "0.0.1",
    scalaVersion := "2.11.8"
  )
}
