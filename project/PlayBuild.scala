import com.malliina.sbtplay.PlayProject
import com.typesafe.sbt.web.Import.{Assets, pipelineStages}
import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.AutoImport.persistLauncher
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import sbt.Keys._
import sbt._
import webscalajs.ScalaJSWeb
import webscalajs.WebScalaJS.autoImport.{scalaJSPipeline, scalaJSProjects}

object PlayBuild {
  lazy val frontend = Project("frontend", file("frontend"))
    .enablePlugins(ScalaJSPlugin, ScalaJSWeb)
    .settings(
      persistLauncher := true,
      libraryDependencies ++= Seq(
        "com.lihaoyi" %%% "scalatags" % "0.6.2",
        "com.lihaoyi" %%% "upickle" % "0.4.3",
        "be.doeraene" %%% "scalajs-jquery" % "0.9.1"
        //"org.scala-js" %%% "scalajs-dom" % "0.9.1"
      )
    )

  lazy val server = PlayProject.default("logstreams").settings(commonSettings: _*)

  val malliinaGroup = "com.malliina"

  lazy val commonSettings = scalaJSSettings ++ Seq(
    organization := malliinaGroup,
    version := "0.0.1",
    scalaVersion := "2.11.8",
    libraryDependencies ++= Seq(
      malliinaGroup %% "util-play" % "3.5.0",
      malliinaGroup %% "logback-rx" % "1.1.0"
    )
  )

  def scalaJSSettings = Seq(
    scalaJSProjects := Seq(frontend),
    pipelineStages in Assets := Seq(scalaJSPipeline)
  )
}
