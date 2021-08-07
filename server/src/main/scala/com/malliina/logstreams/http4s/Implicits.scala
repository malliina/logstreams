package com.malliina.logstreams.http4s

import cats.effect.IO
import com.malliina.html.ScalatagsInstances
import com.malliina.values.Username
import org.http4s.dsl.Http4sDsl
import org.http4s.syntax
import org.http4s.circe.CirceInstances

trait Extractors {
  object UsernameVar {
    def unapply(str: String): Option[Username] =
      if (str.trim.nonEmpty) Option(Username(str.trim)) else None
  }
}

abstract class Implicits[F[_]]
  extends syntax.AllSyntaxBinCompat
  with Http4sDsl[F]
  with Extractors
  with ScalatagsInstances
  with CirceInstances

object Implicits extends Implicits[IO]
