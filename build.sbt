import com.typesafe.sbt.packager.docker.DockerVersion
import sbtbuildinfo.BuildInfoKey
import sbtbuildinfo.BuildInfoKeys.buildInfoKeys
import sbtcrossproject.CrossPlugin.autoImport.{CrossType => PortableType, crossProject => portableProject}
import scalajsbundler.util.JSON
import org.scalajs.sbtplugin.Stage

import scala.sys.process.Process
import scala.util.Try

val malliinaGroup = "com.malliina"
val utilPlayVersion = "6.0.1"
val primitivesVersion = "1.19.0"
val logbackStreamsVersion = "1.8.0"
val playJsonVersion = "2.9.2"
val akkaHttpVersion = "10.1.12"
val munitVersion = "0.7.27"
val testContainersVersion = "0.39.5"

val utilPlayDep = malliinaGroup %% "web-auth" % utilPlayVersion

val serverVersion = "0.6.0"

inThisBuild(
  Seq(
    organization := malliinaGroup,
    scalaVersion := "2.13.6",
    scalacOptions := Seq("-unchecked", "-deprecation"),
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % munitVersion % Test
    ),
    testFrameworks += new TestFramework("munit.Framework")
  )
)

val client = Project("logstreams-client", file("client"))
  .enablePlugins(MavenCentralPlugin)
  .disablePlugins(RevolverPlugin)
  .settings(
    gitUserName := "malliina",
    developerName := "Michael Skogberg",
    libraryDependencies ++= Seq(
      "com.neovisionaries" % "nv-websocket-client" % "2.14",
      "com.malliina" %% "logback-streams" % logbackStreamsVersion,
      "com.malliina" %%% "primitives" % primitivesVersion,
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion
    ),
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
  .enablePlugins(NodeJsPlugin, ClientPlugin)
  .disablePlugins(RevolverPlugin)
  .dependsOn(crossJs)
  .settings(
    assetsPackage := "com.malliina.logstreams",
    version := "1.0.0",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "scalatags" % "0.9.4",
      "com.typesafe.play" %%% "play-json" % playJsonVersion
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
    fullOptJS / webpackConfigFile  := Some(baseDirectory.value / "webpack.prod.config.js"),
    Compile / fastOptJS / webpackBundlingMode := BundlingMode.LibraryOnly(),
    Compile / fullOptJS / webpackBundlingMode := BundlingMode.Application
  )

val prodPort = 9000
val http4sModules = Seq("blaze-server", "blaze-client", "dsl", "scalatags", "play-json")

val server = project
  .in(file("server"))
  .enablePlugins(FileTreePlugin, JavaServerAppPackaging, SystemdPlugin, BuildInfoPlugin, ServerPlugin)
  .dependsOn(crossJvm, client)
  .settings(
    version := serverVersion,
    buildInfoKeys ++= Seq[BuildInfoKey](
      "frontName" -> (frontend / name).value
    ),
    buildInfoPackage := "com.malliina.app",
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, "hash" -> gitHash),
    libraryDependencies ++= http4sModules.map { m =>
      "org.http4s" %% s"http4s-$m" % "0.21.24"
    } ++ Seq("doobie-core", "doobie-hikari").map { d =>
      "org.tpolecat" %% d % "0.13.4"
    } ++ Seq(
      "com.github.pureconfig" %% "pureconfig" % "0.16.0",
      "org.flywaydb" % "flyway-core" % "7.11.2",
      "mysql" % "mysql-connector-java" % "5.1.49",
      "org.slf4j" % "slf4j-api" % "1.7.30",
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "ch.qos.logback" % "logback-core" % "1.2.3",
      "com.malliina" %% "util-html" % utilPlayVersion,
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
    Docker / daemonUser  := "logstreams",
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
    libraryDependencies += "com.typesafe.play" %% "play-ws-standalone" % "2.1.2"
  )

val runApp = inputKey[Unit]("Runs the app")

val logstreamsRoot = project
  .in(file("."))
  .aggregate(frontend, server, client, it)
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
