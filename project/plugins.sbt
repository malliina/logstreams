scalaVersion := "2.12.15"

val utilsVersion = "1.2.5"

Seq(
  "com.malliina" % "sbt-nodejs" % utilsVersion,
  "com.malliina" % "sbt-bundler" % utilsVersion,
  "com.malliina" % "sbt-utils-maven" % utilsVersion,
  "com.malliina" % "sbt-packager" % "2.9.0",
  "com.malliina" % "live-reload" % "0.2.5",
  "com.malliina" % "sbt-filetree" % "0.4.1",
  "com.typesafe.sbt" % "sbt-native-packager" % "1.8.1",
  "org.scala-js" % "sbt-scalajs" % "1.7.0",
  "org.portable-scala" % "sbt-scalajs-crossproject" % "1.1.0",
  "ch.epfl.scala" % "sbt-scalajs-bundler" % "0.20.0",
  "org.scalameta" % "sbt-scalafmt" % "2.4.3",
  "com.eed3si9n" % "sbt-buildinfo" % "0.10.0",
  "io.spray" % "sbt-revolver" % "0.9.1"
) map addSbtPlugin
