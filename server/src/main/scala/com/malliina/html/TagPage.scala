package com.malliina.html

import org.http4s.{Charset, EntityEncoder, MediaType}
import scalatags.Text
import _root_.scalatags.generic.Frag
import org.http4s.headers.`Content-Type`

case class TagPage(tags: Text.TypedTag[String]) {
  override def toString = tags.toString()
}

// Ripped from http4s-scalatags since it's not available for Scala 3
trait ScalatagsInstances {
  implicit def scalatagsEncoder[F[_], C <: Frag[_, String]](implicit
    charset: Charset = Charset.`UTF-8`
  ): EntityEncoder[F, C] =
    contentEncoder(MediaType.text.html)

  private def contentEncoder[F[_], C <: Frag[_, String]](
    mediaType: MediaType
  )(implicit charset: Charset): EntityEncoder[F, C] =
    EntityEncoder
      .stringEncoder[F]
      .contramap[C](content => content.render)
      .withContentType(`Content-Type`(mediaType, charset))
}
