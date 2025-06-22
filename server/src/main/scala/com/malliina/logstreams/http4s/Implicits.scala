package com.malliina.logstreams.http4s

import com.malliina.values.Username
import org.http4s.{Charset, EntityEncoder, MediaType, syntax}
import org.http4s.circe.CirceInstances
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`Content-Type`
import scalatags.generic.Frag

trait Extractors:
  object UsernameVar:
    def unapply(str: String): Option[Username] =
      if str.trim.nonEmpty then Option(Username(str.trim)) else None

trait MyScalatagsInstances:
  given scalatagsEncoder[F[_], C <: Frag[?, String]](using
    charset: Charset = Charset.`UTF-8`
  ): EntityEncoder[F, C] =
    contentEncoder(MediaType.text.html)

  private def contentEncoder[F[_], C <: Frag[?, String]](mediaType: MediaType)(using
    charset: Charset
  ): EntityEncoder[F, C] =
    EntityEncoder
      .stringEncoder[F]
      .contramap[C](content => content.render)
      .withContentType(`Content-Type`(mediaType, charset))

extension [L, R](e: Either[L, R])
  def recover[RR >: R](recover: L => RR): RR =
    e.fold(recover, identity)
