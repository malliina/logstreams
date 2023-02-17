import sbtbuildinfo.BuildInfoKey
import sbtbuildinfo.BuildInfoKeys.buildInfoKeys
import sbtcrossproject.CrossPlugin.autoImport.{CrossType => PortableType, crossProject => portableProject}
import scalajsbundler.util.JSON
import com.comcast.ip4s.IpLiteralSyntax
import scala.sys.process.Process
import scala.util.Try

val malliinaGroup = "com.malliina"
val utilHtmlVersion = "6.5.0"
val primitivesVersion = "3.4.0"
val munitVersion = "0.7.29"
val munitCatsEffectVersion = "1.0.7"
val utilPlayDep = malliinaGroup %% "web-auth" % utilHtmlVersion

val serverVersion = "0.7.0"

val scala3 = "3.2.2"

inThisBuild(
  Seq(
    organization := malliinaGroup,
    scalaVersion := scala3,
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % munitVersion % Test
    ),
    testFrameworks += new TestFramework("munit.Framework"),
    assemblyMergeStrategy := {
      case PathList("META-INF", "versions", xs @ _*) => MergeStrategy.first
      case PathList("com", "malliina", xs @ _*)      => MergeStrategy.first
      case PathList("module-info.class")             => MergeStrategy.discard
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
    libraryDependencies ++= Seq("classic", "core").map { m =>
      "ch.qos.logback" % s"logback-$m" % "1.4.5"
    } ++ Seq(
      "com.malliina" %%% "primitives" % primitivesVersion,
      "co.fs2" %% "fs2-core" % "3.5.0",
      "org.typelevel" %% "munit-cats-effect-3" % munitCatsEffectVersion % Test
    ),
    moduleName := "logback-fs2",
    releaseProcess := tagReleaseProcess.value,
    scalaVersion := scala3,
    crossScalaVersions := scala3 :: Nil,
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
    scalaVersion := scala3,
    crossScalaVersions := scala3 :: Nil,
    releaseCrossBuild := true,
    gitUserName := "malliina",
    developerName := "Michael Skogberg",
    libraryDependencies ++= Seq(
      "com.malliina" %% "okclient-io" % primitivesVersion,
      "org.typelevel" %% "munit-cats-effect-3" % munitCatsEffectVersion % Test
    ),
    releaseProcess := tagReleaseProcess.value
  )

val cross = portableProject(JSPlatform, JVMPlatform)
  .crossType(PortableType.Full)
  .in(file("shared"))
  .settings(
    libraryDependencies ++= Seq("generic", "parser").map { m =>
      "io.circe" %%% s"circe-$m" % "0.14.3"
    } ++ Seq(
      "com.malliina" %%% "primitives" % primitivesVersion,
      "com.lihaoyi" %%% "scalatags" % "0.12.0"
    )
  )
val crossJvm = cross.jvm
val crossJs = cross.js

val isProd = settingKey[Boolean]("isProd")

val frontend = project
  .in(file("frontend"))
  .enablePlugins(NodeJsPlugin, ClientPlugin)
  .disablePlugins(RevolverPlugin)
  .dependsOn(crossJs)
  .settings(
    assetsPackage := "com.malliina.logstreams",
    version := "1.0.0",
    Compile / npmDependencies ++= Seq(
      "@fortawesome/fontawesome-free" -> "6.3.0",
      "@popperjs/core" -> "2.11.6",
      "bootstrap" -> "5.2.3"
    ),
    Compile / npmDevDependencies ++= Seq(
      "autoprefixer" -> "10.4.13",
      "cssnano" -> "5.1.15",
      "css-loader" -> "6.7.3",
      "less" -> "4.1.3",
      "less-loader" -> "11.1.0",
      "mini-css-extract-plugin" -> "2.7.2",
      "postcss" -> "8.4.21",
      "postcss-import" -> "15.1.0",
      "postcss-loader" -> "7.0.2",
      "postcss-preset-env" -> "8.0.1",
      "style-loader" -> "3.3.1",
      "webpack-merge" -> "5.8.0"
    ),
    Compile / additionalNpmConfig := Map(
      "engines" -> JSON.obj("node" -> JSON.str("10.x")),
      "private" -> JSON.bool(true),
      "license" -> JSON.str("BSD")
    ),
    isProd := (Global / scalaJSStage).value == FullOptStage
  )

val server = project
  .in(file("server"))
  .enablePlugins(
    FileTreePlugin,
    BuildInfoPlugin,
    ServerPlugin,
    LiveRevolverPlugin
  )
  .dependsOn(crossJvm, client)
  .settings(
    version := serverVersion,
    buildInfoKeys ++= Seq[BuildInfoKey](
      "frontName" -> (frontend / name).value,
      "gitHash" -> gitHash,
      "assetsDir" -> (frontend / assetsRoot).value,
      "publicFolder" -> (frontend / assetsPrefix).value,
      "mode" -> (if ((frontend / isProd).value) "prod" else "dev"),
      "isProd" -> (frontend / isProd).value
    ),
    buildInfoPackage := "com.malliina.app",
    libraryDependencies ++=
      Seq("ember-server", "circe", "dsl").map { m =>
        "org.http4s" %% s"http4s-$m" % "0.23.18"
      } ++ Seq("core", "hikari").map { m =>
        "org.tpolecat" %% s"doobie-$m" % "1.0.0-RC2"
      } ++ Seq(
        "com.malliina" %% "config" % primitivesVersion,
        "org.flywaydb" % "flyway-core" % "7.15.0",
        "mysql" % "mysql-connector-java" % "8.0.32",
        "com.malliina" %% "util-html" % utilHtmlVersion,
        utilPlayDep,
        utilPlayDep % Test classifier "tests",
        "com.dimafeng" %% "testcontainers-scala-mysql" % "0.40.12" % Test,
        "org.typelevel" %% "munit-cats-effect-3" % munitCatsEffectVersion % Test
      ),
    Compile / packageDoc / publishArtifact := false,
    packageDoc / publishArtifact := false,
    Compile / doc / sources := Seq.empty,
    clientProject := frontend,
    (frontend / Compile / start) := Def.taskIf {
      if ((frontend / Compile / start).inputFileChanges.hasChanges) {
        refreshBrowsers.value
      } else {
        Def.task(streams.value.log.info("No frontend changes.")).value
      }
    }.dependsOn(frontend / start).value,
    Compile / unmanagedResourceDirectories ++= {
      val prodAssets =
        if ((frontend / isProd).value)
          List((frontend / Compile / assetsRoot).value.getParent.toFile)
        else Nil
      (baseDirectory.value / "public") +: prodAssets
    },
    assembly / assemblyJarName := "app.jar",
    liveReloadPort := port"10103"
  )

val it = Project("logstreams-test", file("logstreams-test"))
  .dependsOn(server % "test->test", client)
  .settings(
    libraryDependencies += "com.malliina" %% "okclient-io" % primitivesVersion,
    publish / skip := true,
    publishLocal := {}
  )

val runApp = inputKey[Unit]("Runs the app")

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

def gitHash: String =
  sys.env
    .get("GITHUB_SHA")
    .orElse(Try(Process("git rev-parse HEAD").lineStream.head).toOption)
    .getOrElse("unknown")
