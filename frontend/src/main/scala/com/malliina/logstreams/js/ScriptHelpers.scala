package com.malliina.logstreams.js

import com.malliina.logstreams.models.FrontStrings
import org.scalajs.dom

trait ScriptHelpers extends FrontStrings {
  def getElem[T](id: String) = e(id).asInstanceOf[T]

  def e(id: String) = dom.document.getElementById(id)
}
