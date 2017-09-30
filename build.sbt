import com.malliina.sbtplay.PlayProject
import com.malliina.sbtutils.SbtUtils.{developerName, gitUserName, mavenSettings}
import play.sbt.PlayImport
import sbtbuildinfo.BuildInfoKey
import sbtbuildinfo.BuildInfoKeys.buildInfoKeys

val serverVersion = "0.1.0"

lazy val root = project.in(file("."))
  .settings(basicSettings: _*)
  .aggregate(frontend, server, client, it)
lazy val server = PlayProject.server("logstreams", file("server"))
  .settings(serverSettings: _*)
lazy val frontend = project.in(file("frontend"))
  .settings(frontSettings: _*)
  .enablePlugins(ScalaJSPlugin, ScalaJSWeb)
lazy val client = Project("logstreams-client", file("client"))
  .settings(clientSettings: _*)
lazy val it = Project("logstreams-test", file("logstreams-test"))
  .settings(testSettings: _*)
  .dependsOn(server % "test->test", client)

addCommandAlias("web", ";logstreams/run")

val malliinaGroup = "com.malliina"
val utilPlayDep = malliinaGroup %% "util-play" % "4.3.5"

def frontSettings = Seq(
  version := "0.0.1",
  scalaVersion := "2.12.3",
  scalaJSUseMainModuleInitializer := true,
  libraryDependencies ++= Seq(
    "com.lihaoyi" %%% "scalatags" % "0.6.7",
    "be.doeraene" %%% "scalajs-jquery" % "0.9.2",
    "com.typesafe.play" %%% "play-json" % "2.6.3",
    "com.malliina" %%% "primitives" % "1.3.2",
    "org.scalatest" %%% "scalatest" % "3.0.4" % Test
  )
)

def serverSettings = basicSettings ++ scalaJSSettings ++ Seq(
  version := serverVersion,
  buildInfoKeys += BuildInfoKey("frontName" -> (name in frontend).value),
  libraryDependencies ++= Seq(
    "com.h2database" % "h2" % "1.4.196",
    "com.typesafe.slick" %% "slick" % "3.2.1",
    "com.malliina" %% "logstreams-client" % "0.0.9",
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
    "com.malliina" %%% "primitives" % "1.3.2",
    "org.scalatest" %% "scalatest" % "3.0.4" % Test
  ),
  releaseCrossBuild := true
)

def testSettings = basicSettings ++ Seq(
  libraryDependencies += PlayImport.ws
)

def basicSettings = Seq(
  organization := malliinaGroup,
  scalaVersion := "2.12.3",
  crossScalaVersions := Seq("2.11.11", scalaVersion.value),
  scalacOptions := Seq("-unchecked", "-deprecation")
)
