scalaVersion := "2.12.10"

Seq(
  "com.malliina" % "sbt-play" % "1.7.5",
  "com.malliina" % "play-live-reload" % "0.0.26",
  "com.malliina" % "sbt-utils-maven" % "0.15.7",
  "com.malliina" % "sbt-nodejs" % "0.15.7",
  "org.scala-js" % "sbt-scalajs" % "0.6.31",
  "org.portable-scala" % "sbt-scalajs-crossproject" % "0.6.1",
  "com.vmunier" % "sbt-web-scalajs" % "1.0.10-0.6",
  "ch.epfl.scala" % "sbt-web-scalajs-bundler-sjs06" % "0.16.0",
  "com.typesafe.sbt" % "sbt-gzip" % "1.0.2",
  "com.typesafe.sbt" % "sbt-digest" % "1.1.4",
  "ch.epfl.scala" % "sbt-bloop" % "1.4.0-RC1",
  "org.scalameta" % "sbt-scalafmt" % "2.3.0",
  "com.eed3si9n" % "sbt-buildinfo" % "0.9.0"
) map addSbtPlugin
