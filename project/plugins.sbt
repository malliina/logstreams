scalaVersion := "2.12.12"

Seq(
  "com.malliina" % "sbt-nodejs" % "1.0.0",
  "com.malliina" % "sbt-packager" % "2.9.0",
  "com.malliina" % "sbt-filetree" % "0.4.1",
  "com.malliina" % "sbt-utils-maven" % "1.0.0",
  "com.typesafe.sbt" % "sbt-native-packager" % "1.7.6",
  "org.scala-js" % "sbt-scalajs" % "1.5.0",
  "org.portable-scala" % "sbt-scalajs-crossproject" % "1.0.0",
  "ch.epfl.scala" % "sbt-scalajs-bundler" % "0.20.0",
  "ch.epfl.scala" % "sbt-bloop" % "1.4.8",
  "org.scalameta" % "sbt-scalafmt" % "2.4.2",
  "com.eed3si9n" % "sbt-buildinfo" % "0.10.0",
  "io.spray" % "sbt-revolver" % "0.9.1"
) map addSbtPlugin
