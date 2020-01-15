scalaVersion := "2.12.10"
resolvers ++= Seq(
  ivyRepo("bintray-sbt-plugin-releases", "https://dl.bintray.com/content/sbt/sbt-plugin-releases"),
  ivyRepo("malliina bintray sbt", "https://dl.bintray.com/malliina/sbt-plugins/"),
  Resolver.bintrayRepo("malliina", "maven")
)
classpathTypes += "maven-plugin"
scalacOptions ++= Seq("-unchecked", "-deprecation")

Seq(
  "com.malliina" % "sbt-play" % "1.7.1",
  "com.malliina" % "sbt-utils-maven" % "0.15.2",
  "com.malliina" % "sbt-nodejs" % "0.15.2",
  "org.scala-js" % "sbt-scalajs" % "0.6.31",
  "org.portable-scala" % "sbt-scalajs-crossproject" % "0.6.1",
  "com.vmunier" % "sbt-web-scalajs" % "1.0.10-0.6",
  "ch.epfl.scala" % "sbt-web-scalajs-bundler-sjs06" % "0.16.0",
  "com.typesafe.sbt" % "sbt-gzip" % "1.0.2",
  "com.typesafe.sbt" % "sbt-digest" % "1.1.4",
  "ch.epfl.scala" % "sbt-bloop" % "1.4.0-RC1",
  "org.scalameta" % "sbt-scalafmt" % "2.3.0"
) map addSbtPlugin

def ivyRepo(name: String, urlString: String) =
  Resolver.url(name, url(urlString))(Resolver.ivyStylePatterns)
