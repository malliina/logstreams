import com.comcast.ip4s.IpLiteralSyntax
import sbtbuildinfo.BuildInfoKey
import sbtbuildinfo.BuildInfoKeys.buildInfoKeys
import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}

val malliinaGroup = "com.malliina"

val versions = new {
  val circe = "0.14.10"
  val fs2 = "3.11.0"
  val logback = "1.5.18"
  val mariadbClient = "3.5.3"
  val munit = "1.1.1"
  val munitCatsEffect = "2.1.0"
  val primitives = "3.7.10"
  val scala3 = "3.4.0"
  val scalatags = "0.13.1"
  val server = "0.7.0"
  val webAuth = "6.9.10"
}
val webAuthDep = malliinaGroup %% "web-auth" % versions.webAuth

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
      case x =>
        val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
        oldStrategy(x)
    }
  )
)

val fs2 = project
  .in(file("fs2"))
  .enablePlugins(MavenCentralPlugin)
  .settings(
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % versions.logback,
      "com.malliina" %%% "primitives" % versions.primitives,
      "co.fs2" %% "fs2-core" % versions.fs2,
      "org.typelevel" %% "munit-cats-effect" % versions.munitCatsEffect % Test
    ),
    moduleName := "logback-fs2",
    releaseProcess := tagReleaseProcess.value,
    scalaVersion := versions.scala3,
    gitUserName := "malliina",
    developerName := "Michael Skogberg",
    releaseCrossBuild := true
  )

val client = project
  .in(file("client"))
  .enablePlugins(MavenCentralPlugin)
  .disablePlugins(RevolverPlugin)
  .dependsOn(fs2)
  .settings(
    moduleName := "logstreams-client",
    releaseCrossBuild := true,
    gitUserName := "malliina",
    developerName := "Michael Skogberg",
    libraryDependencies ++= Seq(
      "com.malliina" %% "okclient-io" % versions.primitives,
      "org.typelevel" %% "munit-cats-effect" % versions.munitCatsEffect % Test
    ),
    releaseProcess := tagReleaseProcess.value
  )

val cross = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("shared"))
  .settings(
    libraryDependencies ++= Seq("generic", "parser").map { m =>
      "io.circe" %%% s"circe-$m" % versions.circe
    } ++ Seq(
      "com.malliina" %%% "primitives" % versions.primitives,
      "com.malliina" %%% "util-html" % versions.webAuth,
      "com.lihaoyi" %%% "scalatags" % versions.scalatags
    )
  )
val crossJvm = cross.jvm
val crossJs = cross.js

val installDeps = taskKey[Unit]("Runs npm install")

val frontend = project
  .in(file("frontend"))
  .enablePlugins(NodeJsPlugin, RollupPlugin)
  .disablePlugins(RevolverPlugin)
  .dependsOn(crossJs)
  .settings(
    version := "1.0.0",
    cwd := target.value,
    installDeps := RollupPlugin.npmInstall(npmRoot.value, streams.value.log)
  )

val server = project
  .in(file("server"))
  .enablePlugins(
    FileTreePlugin,
    BuildInfoPlugin,
    ServerPlugin,
    LiveRevolverPlugin,
    DebPlugin
  )
  .dependsOn(crossJvm, client)
  .settings(
    version := versions.server,
    clientProject := frontend,
    hashPackage := "com.malliina.logstreams",
    buildInfoPackage := "com.malliina.app",
    buildInfoKeys ++= Seq[BuildInfoKey](
      "frontName" -> (frontend / name).value
    ),
    libraryDependencies ++=
      Seq("util-html", "database", "util-http4s").map { m =>
        "com.malliina" %% m % versions.webAuth
      } ++ Seq(
        "com.malliina" %% "config" % versions.primitives,
        "org.mariadb.jdbc" % "mariadb-java-client" % versions.mariadbClient,
        webAuthDep,
        webAuthDep % Test classifier "tests",
        "org.typelevel" %% "munit-cats-effect" % versions.munitCatsEffect % Test
      ),
    Compile / packageDoc / publishArtifact := false,
    packageDoc / publishArtifact := false,
    Compile / doc / sources := Seq.empty,
    assembly / assemblyJarName := "app.jar",
    liveReloadPort := port"10103",
    dependentModule := crossJvm,
    Compile / resourceDirectories += io.Path.userHome / ".logstreams",
    Linux / name := "logstreams"
  )

val it = Project("logstreams-test", file("logstreams-test"))
  .dependsOn(server % "test->test", client)
  .settings(
    libraryDependencies += "com.malliina" %% "okclient-io" % versions.primitives,
    publish / skip := true,
    publishLocal := {}
  )

val root = project
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
