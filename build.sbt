import com.malliina.sbtutils.SbtUtils
import com.typesafe.sbt.packager.docker.DockerVersion
import sbtbuildinfo.BuildInfoKey
import sbtbuildinfo.BuildInfoKeys.buildInfoKeys
import sbtcrossproject.CrossPlugin.autoImport.{CrossType => PortableType, crossProject => portableProject}
import scalajsbundler.util.JSON

import scala.sys.process.Process
import scala.util.Try

val malliinaGroup = "com.malliina"
val utilHtmlVersion = "6.0.5"
val primitivesVersion = "3.0.2"
val logbackVersion = "1.2.6"
val munitVersion = "0.7.29"

val utilPlayDep = malliinaGroup %% "web-auth" % utilHtmlVersion

val serverVersion = "0.7.0"

val circeModules = Seq("generic", "parser")
val scala213 = "2.13.6"
val scala3 = "3.1.1"

inThisBuild(
  Seq(
    organization := malliinaGroup,
    scalaVersion := scala3,
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
      "co.fs2" %% "fs2-core" % "3.1.2"
    ),
    moduleName := "logback-fs2",
    releaseProcess := tagReleaseProcess.value,
    scalaVersion := scala3,
    crossScalaVersions := scala3 :: scala213 :: Nil,
    gitUserName := "malliina",
    developerName := "Michael Skogberg",
    releaseCrossBuild := true,
    libraryDependencies ++= SbtUtils.loggingDeps
  )

val client = project
  .in(file("client"))
  .enablePlugins(MavenCentralPlugin)
  .disablePlugins(RevolverPlugin)
  .dependsOn(fs2)
  .settings(
    moduleName := "logstreams-client",
    scalaVersion := scala3,
    crossScalaVersions := scala3 :: scala213 :: Nil,
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
    libraryDependencies ++= circeModules.map(m => "io.circe" %%% s"circe-$m" % "0.14.1") ++ Seq(
      "com.malliina" %%% "primitives" % primitivesVersion,
      ("com.lihaoyi" %%% "scalatags" % "0.9.4").cross(CrossVersion.for3Use2_13)
    )
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
    webpack / version := "4.44.2",
    webpackEmitSourceMaps := false,
    scalaJSUseMainModuleInitializer := true,
    Compile / npmDependencies ++= Seq(
      "@fortawesome/fontawesome-free" -> "5.15.4",
      "@popperjs/core" -> "2.10.2",
      "bootstrap" -> "5.1.3"
    ),
    Compile / npmDevDependencies ++= Seq(
      "autoprefixer" -> "10.2.5",
      "cssnano" -> "4.1.11",
      "css-loader" -> "5.2.1",
      "file-loader" -> "6.2.0",
      "less" -> "4.1.1",
      "less-loader" -> "7.3.0",
      "mini-css-extract-plugin" -> "1.6.2",
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
    ServerPlugin,
    LiveRevolverPlugin
  )
  .dependsOn(crossJvm, client)
  .settings(
    version := serverVersion,
    buildInfoKeys ++= Seq[BuildInfoKey](
      "frontName" -> (frontend / name).value
    ),
    buildInfoPackage := "com.malliina.app",
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, "hash" -> gitHash),
    libraryDependencies ++= SbtUtils.loggingDeps ++ http4sModules.map { m =>
      "org.http4s" %% s"http4s-$m" % "0.23.6"
    } ++ Seq("doobie-core", "doobie-hikari").map { d =>
      "org.tpolecat" %% d % "1.0.0-RC1"
    } ++ Seq(
      "com.typesafe" % "config" % "1.4.1",
      "org.flywaydb" % "flyway-core" % "7.15.0",
      "mysql" % "mysql-connector-java" % "5.1.49",
      "com.malliina" %% "util-html" % utilHtmlVersion,
      utilPlayDep,
      utilPlayDep % Test classifier "tests",
      "com.dimafeng" %% "testcontainers-scala-mysql" % "0.39.8" % Test
    ),
    Universal / javaOptions ++= Seq(
      "-J-Xmx1024m",
      s"-Dhttp.port=$prodPort",
      "-Dlogback.configurationFile=logback-prod.xml"
    ),
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
    libraryDependencies += "com.malliina" %% "okclient-io" % primitivesVersion,
    publish / skip := true,
    publishLocal := {}
  )

val runApp = inputKey[Unit]("Runs the app")

val logstreamsRoot = project
  .in(file("."))
  .aggregate(frontend, server, client, it, fs2)
  .settings(
    start := (server / start).value,
    publishTo := Some(Resolver.file("Unused transient repository", file("target/unusedrepo"))),
    publish / skip := true,
    publishArtifact := false,
    packagedArtifacts := Map.empty,
    publish := {},
    publishLocal := {},
    releaseProcess := (client / tagReleaseProcess).value
  )

Global / onChangedBuildSource := ReloadOnSourceChanges

def gitHash: String =
  sys.env
    .get("GITHUB_SHA")
    .orElse(Try(Process("git rev-parse HEAD").lineStream.head).toOption)
    .getOrElse("unknown")
