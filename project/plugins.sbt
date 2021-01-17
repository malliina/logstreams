scalaVersion := "2.12.12"

Seq(
  "com.malliina" % "sbt-filetree" % "0.4.1",
  "com.typesafe.sbt" % "sbt-native-packager" % "1.7.6",
  "com.malliina" % "sbt-play" % "1.8.0",
  "com.typesafe.play" % "sbt-plugin" % "2.8.2",
  "com.malliina" % "sbt-utils-maven" % "1.0.0",
  "com.malliina" % "sbt-nodejs" % "1.0.0",
  "org.scala-js" % "sbt-scalajs" % "1.4.0",
  "org.portable-scala" % "sbt-scalajs-crossproject" % "1.0.0",
  "com.vmunier" % "sbt-web-scalajs" % "1.0.11",
  "ch.epfl.scala" % "sbt-scalajs-bundler" % "0.20.0",
  "com.typesafe.sbt" % "sbt-gzip" % "1.0.2",
  "com.typesafe.sbt" % "sbt-digest" % "1.1.4",
  "ch.epfl.scala" % "sbt-bloop" % "1.4.6",
  "org.scalameta" % "sbt-scalafmt" % "2.4.2",
  "com.eed3si9n" % "sbt-buildinfo" % "0.10.0",
  "io.spray" % "sbt-revolver" % "0.9.1"
) map addSbtPlugin
