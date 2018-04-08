package com.malliina.logstreams.js

import org.scalajs.dom
import org.scalajs.jquery.{JQuery, jQuery}

trait ScriptHelpers {
  def elem(id: String): JQuery = jQuery(s"#$id")

  def getElem[T](id: String) = dom.document.getElementById(id).asInstanceOf[T]
}
