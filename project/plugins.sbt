scalaVersion := "2.12.4"
resolvers ++= Seq(
  ivyRepo("bintray-sbt-plugin-releases",
    "http://dl.bintray.com/content/sbt/sbt-plugin-releases"),
  ivyRepo("malliina bintray sbt",
    "https://dl.bintray.com/malliina/sbt-plugins/"),
  Resolver.bintrayRepo("malliina", "maven")
)
scalacOptions ++= Seq("-unchecked", "-deprecation")

Seq(
  "com.malliina" % "sbt-play" % "1.2.1",
  "com.malliina" % "sbt-utils" % "0.7.1",
  "org.scala-js" % "sbt-scalajs" % "0.6.21",
  "com.vmunier" % "sbt-web-scalajs" % "1.0.6",
  // "com.eed3si9n" % "sbt-buildinfo" % "0.6.1"
) map addSbtPlugin

def ivyRepo(name: String, urlString: String) =
  Resolver.url(name, url(urlString))(Resolver.ivyStylePatterns)
