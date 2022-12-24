package com.malliina.logstreams.http4s

import _root_.scalatags.generic.Frag
import cats.effect.IO
import com.malliina.html.ScalatagsInstances
import com.malliina.values.Username
import org.http4s.*
import org.http4s.dsl.Http4sDsl
import org.http4s.circe.CirceInstances
import org.http4s.headers.`Content-Type`

trait Extractors:
  object UsernameVar:
    def unapply(str: String): Option[Username] =
      if str.trim.nonEmpty then Option(Username(str.trim)) else None

trait MyScalatagsInstances:
  implicit def scalatagsEncoder[F[_], C <: Frag[?, String]](implicit
    charset: Charset = Charset.`UTF-8`
  ): EntityEncoder[F, C] =
    contentEncoder(MediaType.text.html)

  private def contentEncoder[F[_], C <: Frag[?, String]](mediaType: MediaType)(implicit
    charset: Charset
  ): EntityEncoder[F, C] =
    EntityEncoder
      .stringEncoder[F]
      .contramap[C](content => content.render)
      .withContentType(`Content-Type`(mediaType, charset))

abstract class Implicits[F[_]]
  extends syntax.AllSyntax
  with Http4sDsl[F]
  with Extractors
  with CirceInstances
  with MyScalatagsInstances

object Implicits extends Implicits[IO]
