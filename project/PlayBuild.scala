import com.malliina.sbtplay.PlayProject
import sbt.Keys._
import sbt._

object PlayBuild {
  lazy val p = PlayProject("logstreams").settings(commonSettings: _*)

  val malliinaGroup = "com.malliina"

  lazy val commonSettings = Seq(
    organization := malliinaGroup,
    version := "0.0.1",
    scalaVersion := "2.11.8",
    scalacOptions ++= Seq(
      "-encoding", "UTF-8",
      "-unchecked"
    ),
    libraryDependencies ++= Seq(
      malliinaGroup %% "play-base" % "3.2.1",
      malliinaGroup %% "logback-rx" % "1.0.1",
      "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % Test
    )
  )
}
