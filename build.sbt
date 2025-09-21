import com.comcast.ip4s.IpLiteralSyntax
import sbtbuildinfo.BuildInfoKey
import sbtbuildinfo.BuildInfoKeys.buildInfoKeys
import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}

val malliinaGroup = "com.malliina"

val versions = new {
  val circe = "0.14.14"
  val fs2 = "3.11.0"
  val logback = "1.5.18"
  val mariadbClient = "3.5.6"
  val munit = "1.2.0"
  val munitCatsEffect = "2.1.0"
  val scala3 = "3.4.0"
  val scalatags = "0.13.1"
  val server = "0.7.0"
  val util = "6.10.1"
}
val webAuthDep = malliinaGroup %% "web-auth" % versions.util

inThisBuild(
  Seq(
    organization := malliinaGroup,
    scalaVersion := versions.scala3,
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % versions.munit % Test
    ),
    assemblyMergeStrategy := {
      case PathList("META-INF", "versions", xs @ _*)  => MergeStrategy.first
      case PathList("META-INF", "okio.kotlin_module") => MergeStrategy.first
      case PathList("com", "malliina", xs @ _*)       => MergeStrategy.first
      case PathList("module-info.class")              => MergeStrategy.discard
      case x                                          =>
        val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
        oldStrategy(x)
    }
  )
)

val cross = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("shared"))
  .settings(
    libraryDependencies ++= Seq("generic", "parser").map { m =>
      "io.circe" %%% s"circe-$m" % versions.circe
    } ++ Seq(
      "com.malliina" %%% "primitives" % versions.util,
      "com.malliina" %%% "util-html" % versions.util,
      "com.lihaoyi" %%% "scalatags" % versions.scalatags
    )
  )
val crossJvm = cross.jvm
val crossJs = cross.js

val installDeps = taskKey[Unit]("Runs npm install")

val frontend = project
  .in(file("frontend"))
  .enablePlugins(NodeJsPlugin, EsbuildPlugin)
  .disablePlugins(RevolverPlugin)
  .dependsOn(crossJs)

val server = project
  .in(file("server"))
  .enablePlugins(
    FileTreePlugin,
    BuildInfoPlugin,
    ServerPlugin,
    LiveRevolverPlugin,
    DebPlugin
  )
  .dependsOn(crossJvm)
  .settings(
    version := versions.server,
    clientProject := frontend,
    hashPackage := "com.malliina.logstreams",
    buildInfoPackage := "com.malliina.app",
    buildInfoKeys ++= Seq[BuildInfoKey](
      "frontName" -> (frontend / name).value
    ),
    libraryDependencies ++=
      Seq("config", "logstreams-client", "util-html", "database", "util-http4s", "web-auth").map {
        m =>
          "com.malliina" %% m % versions.util
      } ++ Seq(
        "org.mariadb.jdbc" % "mariadb-java-client" % versions.mariadbClient,
        webAuthDep % Test classifier "tests",
        "org.typelevel" %% "munit-cats-effect" % versions.munitCatsEffect % Test
      ),
    Compile / doc / sources := Seq.empty,
    assembly / assemblyJarName := "app.jar",
    liveReloadPort := port"10103",
    dependentModule := crossJvm,
    Compile / resourceDirectories += io.Path.userHome / ".logstreams",
    Linux / name := "logstreams"
  )

val it = Project("logstreams-test", file("logstreams-test"))
  .dependsOn(server % "test->test")
  .settings(
    libraryDependencies ++= Seq("logstreams-client", "okclient-io").map { m =>
      "com.malliina" %% m % versions.util
    }
  )

val root = project
  .in(file("."))
  .aggregate(frontend, server, it, crossJvm, crossJs)
  .settings(
    start := (server / start).value
  )

Global / onChangedBuildSource := ReloadOnSourceChanges
