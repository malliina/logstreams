import play.sbt.PlayImport
import sbtbuildinfo.BuildInfoKey
import sbtbuildinfo.BuildInfoKeys.buildInfoKeys
import sbtcrossproject.CrossPlugin.autoImport.{
  CrossType => PortableType,
  crossProject => portableProject
}
import scalajsbundler.util.JSON

val malliinaGroup = "com.malliina"
val utilPlayVersion = "5.2.1"
val primitivesVersion = "1.11.0"
val playJsonVersion = "2.7.4"
val akkaHttpVersion = "10.1.8"
val scalaTestVersion = "3.0.8"
val utilPlayDep = malliinaGroup %% "util-play" % utilPlayVersion

val serverVersion = "0.5.0"

val basicSettings = Seq(
  organization := malliinaGroup,
  scalaVersion := "2.13.0",
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
  .enablePlugins(ScalaJSBundlerPlugin, ScalaJSWeb, NodeCheckPlugin)
  .dependsOn(crossJs)
  .settings(basicSettings)
  .settings(
    version := "1.0.0",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "scalatags" % "0.7.0",
      "com.typesafe.play" %%% "play-json" % playJsonVersion,
      "org.scalatest" %%% "scalatest" % scalaTestVersion % Test
    ),
    version in webpack := "4.27.1",
    emitSourceMaps := false,
    scalaJSUseMainModuleInitializer := true,
    webpackBundlingMode := BundlingMode.LibraryOnly(),
    npmDependencies in Compile ++= Seq(
      "@fortawesome/fontawesome-free" -> "5.8.1",
      "bootstrap" -> "4.2.1",
      "jquery" -> "3.3.1",
      "popper.js" -> "1.14.6"
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
    libraryDependencies ++= Seq(
      "com.h2database" % "h2" % "1.4.196",
      "mysql" % "mysql-connector-java" % "5.1.47",
      "com.typesafe.slick" %% "slick" % "3.3.2",
      "com.zaxxer" % "HikariCP" % "3.2.0",
      "com.malliina" %% "logstreams-client" % "1.6.0",
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
    crossScalaVersions := scalaVersion.value :: "2.12.8" :: Nil,
    gitUserName := "malliina",
    developerName := "Michael Skogberg",
    resolvers += "Sonatype releases" at "https://oss.sonatype.org/content/repositories/releases/",
    libraryDependencies ++= Seq(
      "com.neovisionaries" % "nv-websocket-client" % "2.9",
      "com.malliina" %% "logback-streams" % "1.6.0",
      "com.malliina" %%% "primitives" % primitivesVersion,
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
      "org.scalatest" %% "scalatest" % scalaTestVersion % Test
    ),
    releaseCrossBuild := true,
    releaseProcess := tagReleaseProcess.value
  )

val it = Project("logstreams-test", file("logstreams-test"))
  .dependsOn(server % "test->test", client)
  .settings(basicSettings)
  .settings(
    libraryDependencies += "com.typesafe.play" %% "play-ws-standalone" % "2.0.6"
  )

val logstreamsRoot = project
  .in(file("."))
//  .aggregate(frontend, server, client)
  .aggregate(frontend, server, client, it)
  .settings(basicSettings)

addCommandAlias("web", ";logstreams/run")
