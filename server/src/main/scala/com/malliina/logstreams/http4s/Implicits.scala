package com.malliina.logstreams.http4s

import cats.effect.IO
import com.malliina.values.Username
import org.http4s.dsl.Http4sDsl
import org.http4s.play.PlayInstances
import org.http4s.scalatags.ScalatagsInstances
import org.http4s.syntax

trait Extractors {
  object UsernameVar {
    def unapply(str: String): Option[Username] =
      if (str.trim.nonEmpty) Option(Username(str.trim)) else None
  }
}

abstract class Implicits[F[_]]
  extends syntax.AllSyntaxBinCompat
  with Http4sDsl[F]
  with ScalatagsInstances
  with PlayInstances
  with Extractors

object Implicits extends Implicits[IO]
