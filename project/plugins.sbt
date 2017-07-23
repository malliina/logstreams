scalaVersion := "2.10.6"
resolvers ++= Seq(
  ivyRepo("bintray-sbt-plugin-releases",
    "http://dl.bintray.com/content/sbt/sbt-plugin-releases"),
  ivyRepo("malliina bintray sbt",
    "https://dl.bintray.com/malliina/sbt-plugins/"),
  Resolver.bintrayRepo("malliina", "maven")
)
scalacOptions ++= Seq("-unchecked", "-deprecation")

Seq(
  "com.malliina" % "sbt-play" % "1.1.0",
  "com.malliina" % "sbt-utils" % "0.6.3",
  "org.scala-js" % "sbt-scalajs" % "0.6.18",
  "com.vmunier" % "sbt-web-scalajs" % "1.0.5",
  "com.eed3si9n" % "sbt-buildinfo" % "0.6.1"
) map addSbtPlugin

def ivyRepo(name: String, urlString: String) =
  Resolver.url(name, url(urlString))(Resolver.ivyStylePatterns)
