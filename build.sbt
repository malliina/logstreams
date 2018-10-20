import com.malliina.sbtplay.PlayProject
import com.malliina.sbtutils.SbtUtils.{developerName, gitUserName, mavenSettings}
import play.sbt.PlayImport
import sbtbuildinfo.BuildInfoKey
import sbtbuildinfo.BuildInfoKeys.buildInfoKeys
import sbtcrossproject.CrossPlugin.autoImport.{crossProject => portableProject, CrossType => PortableType}

val serverVersion = "0.5.0"

lazy val logstreamsRoot = project.in(file("."))
  .settings(basicSettings: _*)
  .aggregate(frontend, server, client, it)
lazy val server = PlayProject.server("logstreams", file("server"))
  .settings(serverSettings: _*)
  .dependsOn(crossJvm)
lazy val frontend = project.in(file("frontend"))
  .settings(frontSettings: _*)
  .enablePlugins(ScalaJSPlugin, ScalaJSWeb)
  .dependsOn(crossJs)
lazy val cross = portableProject(JSPlatform, JVMPlatform)
  .crossType(PortableType.Full)
  .in(file("shared"))
  .settings(sharedSettings: _*)
lazy val crossJvm = cross.jvm
lazy val crossJs = cross.js
lazy val client = Project("logstreams-client", file("client"))
  .settings(clientSettings: _*)
lazy val it = Project("logstreams-test", file("logstreams-test"))
  .settings(testSettings: _*)
  .dependsOn(server % "test->test", client)

addCommandAlias("web", ";logstreams/run")

val malliinaGroup = "com.malliina"
val utilPlayVersion = "4.16.0"
val primitivesVersion = "1.6.1"
val utilPlayDep = malliinaGroup %% "util-play" % utilPlayVersion

def frontSettings = Seq(
  version := "1.0.0",
  scalaVersion := "2.12.7",
  scalaJSUseMainModuleInitializer := true,
  libraryDependencies ++= Seq(
    "com.lihaoyi" %%% "scalatags" % "0.6.7",
    "be.doeraene" %%% "scalajs-jquery" % "0.9.4",
    "com.typesafe.play" %%% "play-json" % "2.6.10",
    "org.scalatest" %%% "scalatest" % "3.0.5" % Test
  )
)

def serverSettings = basicSettings ++ scalaJSSettings ++ Seq(
  version := serverVersion,
  buildInfoKeys += BuildInfoKey("frontName" -> (name in frontend).value),
  resolvers += "Sonatype releases" at "https://oss.sonatype.org/content/repositories/releases/",
  libraryDependencies ++= Seq(
    "com.h2database" % "h2" % "1.4.196",
    "mysql" % "mysql-connector-java" % "5.1.47",
    "com.typesafe.slick" %% "slick" % "3.2.3",
    "com.zaxxer" % "HikariCP" % "3.2.0",
    "com.malliina" %% "logstreams-client" % "1.2.0",
    "com.malliina" %% "play-social" % utilPlayVersion,
    utilPlayDep,
    utilPlayDep % Test classifier "tests"
  ).map(_.withSources().withJavadoc()),
  dependencyOverrides ++= Seq(
    "com.typesafe.akka" %% "akka-stream" % "2.5.8",
    "com.typesafe.akka" %% "akka-actor" % "2.5.8"
  ),
  pipelineStages := Seq(digest, gzip),
  javaOptions in Universal ++= {
    val linuxName = (name in Linux).value
    Seq(
      s"-Dconfig.file=/etc/$linuxName/production.conf",
      s"-Dlogger.file=/etc/$linuxName/logback-prod.xml",
      "-Dhttp.port=8563"
    )
  },
  linuxPackageSymlinks := linuxPackageSymlinks.value.filterNot(_.link == "/usr/bin/starter"),
  routesImport ++= Seq(
    "com.malliina.values.Username",
    "com.malliina.play.http.Bindables.username"
  )
)

def sharedSettings = basicSettings ++ Seq(
  libraryDependencies ++= Seq(
    "com.typesafe.play" %%% "play-json" % "2.6.10",
    "com.malliina" %%% "primitives" % primitivesVersion
  )
)

def scalaJSSettings = Seq(
  scalaJSProjects := Seq(frontend),
  pipelineStages in Assets := Seq(scalaJSPipeline)
)

def clientSettings = basicSettings ++ mavenSettings ++ Seq(
  gitUserName := "malliina",
  developerName := "Michael Skogberg",
  resolvers += "Sonatype releases" at "https://oss.sonatype.org/content/repositories/releases/",
  libraryDependencies ++= Seq(
    "com.neovisionaries" % "nv-websocket-client" % "2.6",
    "com.malliina" %% "logback-rx" % "1.4.0",
    "com.malliina" %%% "primitives" % primitivesVersion,
//    "com.typesafe.akka" %% "akka-stream" % akkaStreamsVersion,
    "com.typesafe.akka" %% "akka-http" % "10.1.5",
    "com.typesafe.akka" %% "akka-http-spray-json" % "10.1.5",
    "org.scalatest" %% "scalatest" % "3.0.5" % Test
  ),
  releaseCrossBuild := true
)

def testSettings = basicSettings ++ Seq(
  libraryDependencies += PlayImport.ws
)

def basicSettings = Seq(
  organization := malliinaGroup,
  scalaVersion := "2.12.7",
  scalacOptions := Seq("-unchecked", "-deprecation")
)
