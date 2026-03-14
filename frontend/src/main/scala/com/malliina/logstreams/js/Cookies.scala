package com.malliina.logstreams.js

import com.malliina.values.{ErrorMessage, Readable}
import org.scalajs.dom.document

import scala.scalajs.js.URIUtils

object Cookies:
  def readCookie[T](key: String)(using r: Readable[T]): Either[ErrorMessage, T] =
    cookies.get(key).toRight(ErrorMessage(s"Not found: '$key'.")).flatMap(c => r.read(c))

  private def cookies: Map[String, String] = URIUtils
    .decodeURIComponent(document.cookie)
    .split(";")
    .toList
    .map(_.trim.split("=").toList)
    .collect:
      case key :: value :: _ => key -> value
    .toMap
