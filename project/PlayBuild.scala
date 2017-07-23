import com.malliina.sbtplay.PlayProject
import com.malliina.sbtutils.SbtUtils._
import com.typesafe.sbt.web.Import.{Assets, pipelineStages}
import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import play.sbt.PlayImport
import sbt.Keys._
import sbt._
import sbtbuildinfo.BuildInfoKeys.buildInfoKeys
import sbtbuildinfo.BuildInfoKey
import sbtrelease.ReleasePlugin
import webscalajs.ScalaJSWeb
import webscalajs.WebScalaJS.autoImport.{scalaJSPipeline, scalaJSProjects}

object PlayBuild {
  val serverVersion = "0.0.9"

  lazy val root = Project("root", file("."))
    .settings(basicSettings: _*)
    .aggregate(frontend, server, client, integrationTest)

  lazy val server = PlayProject.server("logstreams", file("server"))
    .settings(serverSettings: _*)

  lazy val frontend = Project("frontend", file("frontend"))
    .settings(frontSettings: _*)
    .enablePlugins(ScalaJSPlugin, ScalaJSWeb)

  lazy val client = Project("logstreams-client", file("client"))
    .settings(clientSettings: _*)

  lazy val integrationTest = Project("logstreams-test", file("logstreams-test"))
    .settings(testSettings: _*)
    .dependsOn(server % "test->test", client)

  val malliinaGroup = "com.malliina"
  val utilPlayDep = malliinaGroup %% "util-play" % "4.1.1"

  def frontSettings = Seq(
    version := "0.0.1",
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "scalatags" % "0.6.5",
      "com.lihaoyi" %%% "upickle" % "0.4.4",
      "com.lihaoyi" %%% "utest" % "0.4.8" % Test,
      "be.doeraene" %%% "scalajs-jquery" % "0.9.2"
    ),
    testFrameworks += new TestFramework("utest.runner.Framework")
  )

  def serverSettings = basicSettings ++ scalaJSSettings ++ Seq(
    buildInfoKeys += BuildInfoKey("frontName" -> (name in frontend).value),
    libraryDependencies ++= Seq(
      "com.h2database" % "h2" % "1.4.196",
      "com.typesafe.slick" %% "slick" % "3.2.1",
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
    libraryDependencies ++= Seq(
      "com.neovisionaries" % "nv-websocket-client" % "2.3",
      "com.malliina" %% "logback-rx" % "1.2.0",
      "org.scalatest" %% "scalatest" % "3.0.3" % Test
    ),
    ReleasePlugin.autoImport.releaseCrossBuild := true
  )

  def testSettings = basicSettings ++ Seq(
    libraryDependencies += PlayImport.ws
  )

  def basicSettings = Seq(
    organization := malliinaGroup,
    version := serverVersion,
    scalaVersion := "2.12.2",
    crossScalaVersions := Seq("2.11.11", scalaVersion.value),
    scalacOptions := Seq("-unchecked", "-deprecation")
  )
}
