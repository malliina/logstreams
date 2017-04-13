import sbt._
import sbt.Keys._

object BuildBuild {
  // "build.sbt" goes here
  lazy val settings = sbtPlugins ++ Seq(
    scalaVersion := "2.10.6",
    resolvers ++= Seq(
      ivyRepo("bintray-sbt-plugin-releases",
        "http://dl.bintray.com/content/sbt/sbt-plugin-releases"),
      ivyRepo("malliina bintray sbt",
        "https://dl.bintray.com/malliina/sbt-plugins/")
    ),
    scalacOptions ++= Seq("-unchecked", "-deprecation")
  )

  def ivyRepo(name: String, urlString: String) =
    Resolver.url(name, url(urlString))(Resolver.ivyStylePatterns)

  def sbtPlugins = Seq(
    "com.malliina" % "sbt-play" % "0.9.3",
    "com.malliina" % "sbt-utils" % "0.6.1",
    "org.scala-js" % "sbt-scalajs" % "0.6.15",
    "com.vmunier" % "sbt-web-scalajs" % "1.0.3",
    "com.eed3si9n" % "sbt-buildinfo" % "0.4.0"
  ) map addSbtPlugin
}
