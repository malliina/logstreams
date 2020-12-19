import com.typesafe.sbt.packager.docker.DockerVersion
import sbtbuildinfo.BuildInfoKey
import sbtbuildinfo.BuildInfoKeys.buildInfoKeys
import sbtcrossproject.CrossPlugin.autoImport.{
  CrossType => PortableType,
  crossProject => portableProject
}
import scalajsbundler.util.JSON

import scala.sys.process.Process
import scala.util.Try

val malliinaGroup = "com.malliina"
val utilPlayVersion = "5.11.0"
val primitivesVersion = "1.17.0"
val logbackStreamsVersion = "1.8.0"
val playJsonVersion = "2.9.1"
val akkaHttpVersion = "10.1.12"
val munitVersion = "0.7.12"

val utilPlayDep = malliinaGroup %% "util-play" % utilPlayVersion

val serverVersion = "0.6.0"

inThisBuild(
  Seq(
    organization := malliinaGroup,
    scalaVersion := "2.13.3",
    scalacOptions := Seq("-unchecked", "-deprecation"),
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % munitVersion % Test
    ),
    testFrameworks += new TestFramework("munit.Framework")
  )
)

val client = Project("logstreams-client", file("client"))
  .enablePlugins(MavenCentralPlugin)
  .settings(
    crossScalaVersions := scalaVersion.value :: "2.12.12" :: Nil,
    gitUserName := "malliina",
    developerName := "Michael Skogberg",
    libraryDependencies ++= Seq(
      "com.neovisionaries" % "nv-websocket-client" % "2.10",
      "com.malliina" %% "logback-streams" % logbackStreamsVersion,
      "com.malliina" %%% "primitives" % primitivesVersion,
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion
    ),
    releaseCrossBuild := true,
    releaseProcess := tagReleaseProcess.value
  )

val cross = portableProject(JSPlatform, JVMPlatform)
  .crossType(PortableType.Full)
  .in(file("shared"))
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.play" %%% "play-json" % playJsonVersion,
      "com.malliina" %%% "primitives" % primitivesVersion
    ),
    testFrameworks += new TestFramework("munit.Framework")
  )
val crossJvm = cross.jvm
val crossJs = cross.js

val frontend = project
  .in(file("frontend"))
  .enablePlugins(ScalaJSBundlerPlugin, ScalaJSWeb, NodeJsPlugin)
  .dependsOn(crossJs)
  .settings(
    version := "1.0.0",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "scalatags" % "0.9.1",
      "com.typesafe.play" %%% "play-json" % playJsonVersion
    ),
    version in webpack := "4.44.2",
    webpackEmitSourceMaps := false,
    scalaJSUseMainModuleInitializer := true,
    webpackBundlingMode := BundlingMode.LibraryOnly(),
    npmDependencies in Compile ++= Seq(
      "@fortawesome/fontawesome-free" -> "5.14.0",
      "bootstrap" -> "4.5.2",
      "jquery" -> "3.5.1",
      "popper.js" -> "1.16.1"
    ),
    npmDevDependencies in Compile ++= Seq(
      "autoprefixer" -> "10.0.0",
      "cssnano" -> "4.1.10",
      "css-loader" -> "4.3.0",
      "file-loader" -> "6.1.0",
      "less" -> "3.12.2",
      "less-loader" -> "7.0.1",
      "mini-css-extract-plugin" -> "0.11.2",
      "postcss" -> "8.0.5",
      "postcss-import" -> "12.0.1",
      "postcss-loader" -> "4.0.2",
      "postcss-preset-env" -> "6.7.0",
      "style-loader" -> "1.2.1",
      "url-loader" -> "4.1.0",
      "webpack-merge" -> "5.1.4"
    ),
    additionalNpmConfig in Compile := Map(
      "engines" -> JSON.obj("node" -> JSON.str("10.x")),
      "private" -> JSON.bool(true),
      "license" -> JSON.str("BSD")
    ),
    webpackConfigFile in fastOptJS := Some(baseDirectory.value / "webpack.dev.config.js"),
    webpackConfigFile in fullOptJS := Some(baseDirectory.value / "webpack.prod.config.js")
  )

val prodPort = 9000

val server = Project("logstreams", file("server"))
  .enablePlugins(WebScalaJSBundlerPlugin, PlayLinuxPlugin)
  .dependsOn(crossJvm, client)
  .settings(
    scalaJSProjects := Seq(frontend),
    pipelineStages in Assets := Seq(scalaJSPipeline),
    version := serverVersion,
    buildInfoKeys ++= Seq[BuildInfoKey](
      "frontName" -> (name in frontend).value
    ),
    buildInfoPackage := "com.malliina.app",
    libraryDependencies ++= Seq("doobie-core", "doobie-hikari").map { d =>
      "org.tpolecat" %% d % "0.9.2"
    } ++ Seq(
      "org.flywaydb" % "flyway-core" % "6.5.6",
      "mysql" % "mysql-connector-java" % "5.1.49",
      "com.malliina" %% "play-social" % utilPlayVersion,
      "com.dimafeng" %% "testcontainers-scala-mysql" % "0.38.3" % Test,
      ws % Test,
      utilPlayDep,
      utilPlayDep % Test classifier "tests"
    ),
    pipelineStages := Seq(digest, gzip),
    javaOptions in Universal ++= {
      Seq(
        "-J-Xmx1024m",
        "-Dpidfile.path=/dev/null",
        "-Dlogger.resource=logback-prod.xml",
        s"-Dhttp.port=$prodPort"
      )
    },
    linuxPackageSymlinks := linuxPackageSymlinks.value.filterNot(_.link == "/usr/bin/starter"),
    routesImport ++= Seq(
      "com.malliina.values.Username",
      "com.malliina.play.http.Bindables.username"
    ),
    httpPort in Linux := Option(s"$prodPort"),
    dockerVersion := Option(DockerVersion(19, 3, 5, None)),
    dockerBaseImage := "openjdk:11",
    daemonUser in Docker := "logstreams",
    version in Docker := gitHash,
    dockerRepository := Option("malliinalogstreams.azurecr.io"),
    dockerExposedPorts ++= Seq(prodPort)
  )

val it = Project("logstreams-test", file("logstreams-test"))
  .dependsOn(server % "test->test", client)
  .settings(
    libraryDependencies += "com.typesafe.play" %% "play-ws-standalone" % "2.1.2"
  )

val logstreamsRoot = project
  .in(file("."))
  .aggregate(frontend, server, client, it)

addCommandAlias("web", ";logstreams/run")

Global / onChangedBuildSource := ReloadOnSourceChanges

def gitHash: String =
  sys.env
    .get("GITHUB_SHA")
    .orElse(Try(Process("git rev-parse HEAD").lineStream.head).toOption)
    .getOrElse("unknown")
