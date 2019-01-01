package com.malliina.logstreams.js

import com.malliina.logstreams.models.FrontStrings
import org.scalajs.dom
import org.scalajs.jquery.JQuery

trait ScriptHelpers extends FrontStrings {
  def elem(id: String): JQuery = MyJQuery(s"#$id")

  def getElem[T](id: String) = e(id).asInstanceOf[T]

  def e(id: String) = dom.document.getElementById(id)
}
