import com.comcast.ip4s.IpLiteralSyntax
import sbtbuildinfo.BuildInfoKey
import sbtbuildinfo.BuildInfoKeys.buildInfoKeys
import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}

val malliinaGroup = "com.malliina"
val webAuthVersion = "6.9.6"
val primitivesVersion = "3.7.5"
val munitVersion = "1.0.4"
val munitCatsEffectVersion = "2.0.0"
val webAuthDep = malliinaGroup %% "web-auth" % webAuthVersion

val serverVersion = "0.7.0"

val scala3 = "3.4.0"

inThisBuild(
  Seq(
    organization := malliinaGroup,
    scalaVersion := scala3,
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % munitVersion % Test
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
      "ch.qos.logback" % "logback-classic" % "1.5.16",
      "com.malliina" %%% "primitives" % primitivesVersion,
      "co.fs2" %% "fs2-core" % "3.11.0",
      "org.typelevel" %% "munit-cats-effect" % munitCatsEffectVersion % Test
    ),
    moduleName := "logback-fs2",
    releaseProcess := tagReleaseProcess.value,
    scalaVersion := scala3,
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
      "com.malliina" %% "okclient-io" % primitivesVersion,
      "org.typelevel" %% "munit-cats-effect" % munitCatsEffectVersion % Test
    ),
    releaseProcess := tagReleaseProcess.value
  )

val cross = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("shared"))
  .settings(
    libraryDependencies ++= Seq("generic", "parser").map { m =>
      "io.circe" %%% s"circe-$m" % "0.14.10"
    } ++ Seq(
      "com.malliina" %%% "primitives" % primitivesVersion,
      "com.malliina" %%% "util-html" % webAuthVersion,
      "com.lihaoyi" %%% "scalatags" % "0.13.1"
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
    version := serverVersion,
    clientProject := frontend,
    hashPackage := "com.malliina.logstreams",
    buildInfoPackage := "com.malliina.app",
    buildInfoKeys ++= Seq[BuildInfoKey](
      "frontName" -> (frontend / name).value
    ),
    libraryDependencies ++=
      Seq("util-html", "database", "util-http4s").map { m =>
        "com.malliina" %% m % webAuthVersion
      } ++ Seq(
        "com.malliina" %% "config" % primitivesVersion,
        "mysql" % "mysql-connector-java" % "8.0.33",
        webAuthDep,
        webAuthDep % Test classifier "tests",
        "org.typelevel" %% "munit-cats-effect" % munitCatsEffectVersion % Test
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
    libraryDependencies += "com.malliina" %% "okclient-io" % primitivesVersion,
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
