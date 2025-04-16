scalaVersion := "2.12.20"

val utilsVersion = "1.6.47"

Seq(
  "com.malliina" % "sbt-revolver-rollup" % utilsVersion,
  "com.malliina" % "sbt-utils-maven" % utilsVersion,
  "com.malliina" % "sbt-filetree" % utilsVersion,
  "com.malliina" % "sbt-nodejs" % utilsVersion,
  "org.portable-scala" % "sbt-scalajs-crossproject" % "1.3.2",
  "org.scalameta" % "sbt-scalafmt" % "2.5.4",
  "com.eed3si9n" % "sbt-assembly" % "2.3.1",
  "com.github.sbt" % "sbt-native-packager" % "1.11.1"
) map addSbtPlugin
