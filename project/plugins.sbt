scalaVersion := "2.12.8"
resolvers ++= Seq(
  ivyRepo("bintray-sbt-plugin-releases",
    "http://dl.bintray.com/content/sbt/sbt-plugin-releases"),
  ivyRepo("malliina bintray sbt",
    "https://dl.bintray.com/malliina/sbt-plugins/"),
  Resolver.bintrayRepo("malliina", "maven")
)
classpathTypes += "maven-plugin"
scalacOptions ++= Seq("-unchecked", "-deprecation")

Seq(
  "com.malliina" % "sbt-play" % "1.6.0",
  "com.malliina" % "sbt-utils-maven" % "0.12.1",
  "org.scala-js" % "sbt-scalajs" % "0.6.27",
  "org.portable-scala" % "sbt-scalajs-crossproject" % "0.6.0",
  "com.vmunier" % "sbt-web-scalajs" % "1.0.6",
  "ch.epfl.scala" % "sbt-web-scalajs-bundler" % "0.14.0",
  "com.typesafe.sbt" % "sbt-gzip" % "1.0.2",
  "com.typesafe.sbt" % "sbt-digest" % "1.1.4",
  "com.typesafe.sbt" % "sbt-less" % "1.1.2"
) map addSbtPlugin

def ivyRepo(name: String, urlString: String) =
  Resolver.url(name, url(urlString))(Resolver.ivyStylePatterns)
