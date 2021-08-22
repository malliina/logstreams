import com.typesafe.sbt.packager.docker.DockerVersion
import sbtbuildinfo.BuildInfoKey
import sbtbuildinfo.BuildInfoKeys.buildInfoKeys
import sbtcrossproject.CrossPlugin.autoImport.{CrossType => PortableType, crossProject => portableProject}
import scalajsbundler.util.JSON

import scala.sys.process.Process
import scala.util.Try

val malliinaGroup = "com.malliina"
val utilHtmlVersion = "6.0.2"
val primitivesVersion = "2.0.2"
val logbackVersion = "1.2.4"
val munitVersion = "0.7.27"
val testContainersVersion = "0.39.5"

val utilPlayDep = malliinaGroup %% "web-auth" % utilHtmlVersion

val serverVersion = "0.7.0"

val logbackModules = Seq("classic", "core")
val circeModules = Seq("generic", "parser")

inThisBuild(
  Seq(
    organization := malliinaGroup,
//    scalaVersion := "3.0.1",
    scalaVersion := "2.13.6",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % munitVersion % Test
    ),
    testFrameworks += new TestFramework("munit.Framework")
  )
)

val fs2 = project
  .in(file("fs2"))
  .enablePlugins(MavenCentralPlugin)
  .settings(
    libraryDependencies ++= Seq(
      "com.malliina" %%% "primitives" % primitivesVersion,
      "co.fs2" %% "fs2-core" % "2.5.9"
    ),
    moduleName := "logback-fs2",
    releaseProcess := tagReleaseProcess.value,
    scalaVersion := "2.13.6",
    crossScalaVersions := "2.13.6" :: "3.0.1" :: Nil,
    gitUserName := "malliina",
    developerName := "Michael Skogberg",
    releaseCrossBuild := true,
    libraryDependencies ++=
      logbackModules.map(m => "ch.qos.logback" % s"logback-$m" % "1.2.4") ++ Seq(
        "org.slf4j" % "slf4j-api" % "1.7.30"
      )
  )

val client = project
  .in(file("client"))
  .enablePlugins(MavenCentralPlugin)
  .disablePlugins(RevolverPlugin)
  .dependsOn(fs2)
  .settings(
    moduleName := "logstreams-client",
    scalaVersion := "2.13.6",
    crossScalaVersions := "2.13.6" :: "3.0.1" :: Nil,
    releaseCrossBuild := true,
    gitUserName := "malliina",
    developerName := "Michael Skogberg",
    libraryDependencies ++= Seq(
      "com.neovisionaries" % "nv-websocket-client" % "2.14",
      "com.malliina" %% "okclient-io" % primitivesVersion
    ),
    releaseProcess := tagReleaseProcess.value
  )

val cross = portableProject(JSPlatform, JVMPlatform)
  .crossType(PortableType.Full)
  .in(file("shared"))
  .settings(
    libraryDependencies ++= Seq(
      "com.malliina" %%% "primitives" % primitivesVersion
    ),
    testFrameworks += new TestFramework("munit.Framework")
  )
val crossJvm = cross.jvm
val crossJs = cross.js

val frontend = project
  .in(file("frontend"))
  .enablePlugins(NodeJsPlugin, ClientPlugin)
  .disablePlugins(RevolverPlugin)
  .dependsOn(crossJs)
  .settings(
    assetsPackage := "com.malliina.logstreams",
    version := "1.0.0",
    libraryDependencies ++= Seq(
//      ("com.lihaoyi" %%% "scalatags" % "0.9.4").cross(CrossVersion.for3Use2_13)
      "com.lihaoyi" %%% "scalatags" % "0.9.4"
    ),
    webpack / version := "4.44.2",
    webpackEmitSourceMaps := false,
    scalaJSUseMainModuleInitializer := true,
    Compile / npmDependencies ++= Seq(
      "@fortawesome/fontawesome-free" -> "5.15.2",
      "bootstrap" -> "4.6.0",
      "jquery" -> "3.6.0",
      "popper.js" -> "1.16.1"
    ),
    Compile / npmDevDependencies ++= Seq(
      "autoprefixer" -> "10.2.5",
      "cssnano" -> "4.1.11",
      "css-loader" -> "5.2.1",
      "file-loader" -> "6.2.0",
      "less" -> "4.1.1",
      "less-loader" -> "7.3.0",
      "mini-css-extract-plugin" -> "1.4.1",
      "postcss" -> "8.2.9",
      "postcss-import" -> "14.0.1",
      "postcss-loader" -> "4.2.0",
      "postcss-preset-env" -> "6.7.0",
      "style-loader" -> "2.0.0",
      "url-loader" -> "4.1.1",
      "webpack-merge" -> "5.7.3"
    ),
    Compile / additionalNpmConfig := Map(
      "engines" -> JSON.obj("node" -> JSON.str("10.x")),
      "private" -> JSON.bool(true),
      "license" -> JSON.str("BSD")
    ),
    fastOptJS / webpackConfigFile := Some(baseDirectory.value / "webpack.dev.config.js"),
    fullOptJS / webpackConfigFile := Some(baseDirectory.value / "webpack.prod.config.js"),
    Compile / fastOptJS / webpackBundlingMode := BundlingMode.LibraryOnly(),
    Compile / fullOptJS / webpackBundlingMode := BundlingMode.Application
  )

val prodPort = 9000
val http4sModules = Seq("blaze-server", "blaze-client", "circe", "dsl")

val server = project
  .in(file("server"))
  .enablePlugins(
    FileTreePlugin,
    JavaServerAppPackaging,
    SystemdPlugin,
    BuildInfoPlugin,
    ServerPlugin
//    LiveReloadPlugin
  )
  .dependsOn(crossJvm, client)
  .settings(
    version := serverVersion,
    buildInfoKeys ++= Seq[BuildInfoKey](
      "frontName" -> (frontend / name).value
    ),
    buildInfoPackage := "com.malliina.app",
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, "hash" -> gitHash),
    libraryDependencies ++= http4sModules.map { m =>
      "org.http4s" %% s"http4s-$m" % "0.22.2"
    } ++ Seq("doobie-core", "doobie-hikari").map { d =>
      "org.tpolecat" %% d % "0.13.4"
    } ++ Seq(
      "com.typesafe" % "config" % "1.4.1",
      "org.flywaydb" % "flyway-core" % "7.11.2",
      "mysql" % "mysql-connector-java" % "5.1.49",
      "org.slf4j" % "slf4j-api" % "1.7.30",
      "ch.qos.logback" % "logback-classic" % logbackVersion,
      "ch.qos.logback" % "logback-core" % logbackVersion,
      "com.malliina" %% "util-html" % utilHtmlVersion,
      utilPlayDep,
      utilPlayDep % Test classifier "tests",
      "com.dimafeng" %% "testcontainers-scala-mysql" % testContainersVersion % Test
    ),
    Universal / javaOptions ++= {
      Seq(
        "-J-Xmx1024m",
        s"-Dhttp.port=$prodPort",
        "-Dlogback.configurationFile=logback-prod.xml"
      )
    },
    Compile / unmanagedResourceDirectories += baseDirectory.value / "public",
    Linux / httpPort := Option(s"$prodPort"),
    dockerVersion := Option(DockerVersion(19, 3, 5, None)),
    dockerBaseImage := "openjdk:11",
    Docker / daemonUser := "logstreams",
    Docker / version := gitHash,
    dockerRepository := Option("malliinacr.azurecr.io"),
    dockerExposedPorts ++= Seq(prodPort),
    Compile / packageDoc / publishArtifact := false,
    packageDoc / publishArtifact := false,
    Compile / doc / sources := Seq.empty,
    Docker / packageName := "logstreams",
    clientProject := frontend
  )

val it = Project("logstreams-test", file("logstreams-test"))
  .dependsOn(server % "test->test", client)
  .settings(
    libraryDependencies += "com.malliina" %% "okclient-io" % primitivesVersion
  )

val runApp = inputKey[Unit]("Runs the app")

val logstreamsRoot = project
  .in(file("."))
  .aggregate(frontend, server, client, it, fs2)
  .settings(
    start := (server / start).value
  )

addCommandAlias("web", ";logstreams/run")

Global / onChangedBuildSource := ReloadOnSourceChanges

def gitHash: String =
  sys.env
    .get("GITHUB_SHA")
    .orElse(Try(Process("git rev-parse HEAD").lineStream.head).toOption)
    .getOrElse("unknown")
