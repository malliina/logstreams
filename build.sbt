import play.sbt.PlayImport
import sbtbuildinfo.BuildInfoKey
import sbtbuildinfo.BuildInfoKeys.buildInfoKeys
import sbtcrossproject.CrossPlugin.autoImport.{
  CrossType => PortableType,
  crossProject => portableProject
}
import scalajsbundler.util.JSON

import scala.sys.process.Process

val serverVersion = "0.5.0"
val malliinaGroup = "com.malliina"
val utilPlayVersion = "5.0.0"
val primitivesVersion = "1.8.1"
val playJsonVersion = "2.7.1"
val akkaHttpVersion = "10.1.7"
val utilPlayDep = malliinaGroup %% "util-play" % utilPlayVersion

val ensureNode = taskKey[Unit]("Make sure the user uses the correct version of node.js")

ensureNode := {
  val log = streams.value.log
  val nodeVersion = Process("node --version")
    .lineStream(log)
    .toList
    .headOption
    .getOrElse(sys.error(s"Unable to resolve node version."))
  val validPrefixes = Seq("v8")
  if (validPrefixes.exists(p => nodeVersion.startsWith(p))) {
    log.info(s"Using node $nodeVersion")
  } else {
    log.info(s"Node $nodeVersion is unlikely to work. Trying to change version using nvm...")
    Process("nvm use 8").run(log).exitValue()
  }
}

val startupTransition: State => State = { s: State =>
  "logstreamsRoot/ensureNode" :: s
}

val basicSettings = Seq(
  organization := malliinaGroup,
  scalaVersion := "2.12.8",
  scalacOptions := Seq("-unchecked", "-deprecation")
)

val cross = portableProject(JSPlatform, JVMPlatform)
  .crossType(PortableType.Full)
  .in(file("shared"))
  .settings(basicSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.play" %%% "play-json" % playJsonVersion,
      "com.malliina" %%% "primitives" % primitivesVersion
    )
  )
val crossJvm = cross.jvm
val crossJs = cross.js

val frontend = project
  .in(file("frontend"))
  .enablePlugins(ScalaJSBundlerPlugin, ScalaJSWeb)
  .dependsOn(crossJs)
  .settings(basicSettings)
  .settings(
    version := "1.0.0",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "scalatags" % "0.6.7",
      "be.doeraene" %%% "scalajs-jquery" % "0.9.4",
      "com.typesafe.play" %%% "play-json" % playJsonVersion,
      "org.scalatest" %%% "scalatest" % "3.0.5" % Test
    ),
    version in webpack := "4.27.1",
    emitSourceMaps := false,
    scalaJSUseMainModuleInitializer := true,
    webpackBundlingMode := BundlingMode.LibraryOnly(),
    npmDependencies in Compile ++= Seq(
      "bootstrap" -> "4.2.1",
      "jquery" -> "3.3.1",
      "popper.js" -> "1.14.6",
      "@fortawesome/fontawesome-free" -> "5.8.1"
    ),
    npmDevDependencies in Compile ++= Seq(
      "autoprefixer" -> "9.4.3",
      "cssnano" -> "4.1.8",
      "css-loader" -> "2.1.0",
      "file-loader" -> "3.0.1",
      "less" -> "3.9.0",
      "less-loader" -> "4.1.0",
      "mini-css-extract-plugin" -> "0.5.0",
      "postcss-import" -> "12.0.1",
      "postcss-loader" -> "3.0.0",
      "postcss-preset-env" -> "6.5.0",
      "style-loader" -> "0.23.1",
      "url-loader" -> "1.1.2",
      "webpack-merge" -> "4.1.5"
    ),
    additionalNpmConfig in Compile := Map(
      "engines" -> JSON.obj("node" -> JSON.str("8.x")),
      "private" -> JSON.bool(true),
      "license" -> JSON.str("BSD")
    ),
    webpackConfigFile in fastOptJS := Some(baseDirectory.value / "webpack.dev.config.js"),
    webpackConfigFile in fullOptJS := Some(baseDirectory.value / "webpack.prod.config.js")
  )

val server = Project("logstreams", file("server"))
  .enablePlugins(WebScalaJSBundlerPlugin, PlayServerPlugin)
  .dependsOn(crossJvm)
  .settings(basicSettings)
  .settings(
    scalaJSProjects := Seq(frontend),
    pipelineStages in Assets := Seq(scalaJSPipeline),
    version := serverVersion,
    buildInfoKeys += BuildInfoKey("frontName" -> (name in frontend).value),
    resolvers += "Sonatype releases" at "https://oss.sonatype.org/content/repositories/releases/",
    libraryDependencies ++= Seq(
      "com.h2database" % "h2" % "1.4.196",
      "mysql" % "mysql-connector-java" % "5.1.47",
      "com.typesafe.slick" %% "slick" % "3.2.3",
      "com.zaxxer" % "HikariCP" % "3.2.0",
      "com.malliina" %% "logstreams-client" % "1.5.0",
      "com.malliina" %% "play-social" % utilPlayVersion,
      utilPlayDep,
      utilPlayDep % Test classifier "tests"
    ).map(_.withSources().withJavadoc()),
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

val client = Project("logstreams-client", file("client"))
  .enablePlugins(MavenCentralPlugin)
  .settings(basicSettings)
  .settings(
    gitUserName := "malliina",
    developerName := "Michael Skogberg",
    resolvers += "Sonatype releases" at "https://oss.sonatype.org/content/repositories/releases/",
    libraryDependencies ++= Seq(
      "com.neovisionaries" % "nv-websocket-client" % "2.6",
      "com.malliina" %% "logback-streams" % "1.5.0",
      "com.malliina" %%% "primitives" % primitivesVersion,
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
      "org.scalatest" %% "scalatest" % "3.0.5" % Test
    ),
    releaseCrossBuild := true
  )

val it = Project("logstreams-test", file("logstreams-test"))
  .dependsOn(server % "test->test", client)
  .settings(basicSettings)
  .settings(
    libraryDependencies += PlayImport.ws
  )

val logstreamsRoot = project
  .in(file("."))
  .aggregate(frontend, server, client, it)
  .settings(basicSettings)
  .settings(
    onLoad in Global := {
      val old = (onLoad in Global).value
      startupTransition compose old
    }
  )

addCommandAlias("web", ";logstreams/run")
