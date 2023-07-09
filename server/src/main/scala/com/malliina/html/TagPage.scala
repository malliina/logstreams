package com.malliina.html

import _root_.scalatags.generic.Frag
import org.http4s.headers.`Content-Type`
import org.http4s.{Charset, EntityEncoder, MediaType}
import scalatags.Text

case class TagPage(tags: Text.TypedTag[String]):
  override def toString = tags.toString()
