package com.malliina.logstreams.js

import com.malliina.logstreams.models.FrontStrings
import org.scalajs.dom
import org.scalajs.dom.Element

object ScriptHelpers extends ScriptHelpers

trait ScriptHelpers extends FrontStrings:
  def getElem[T <: Element](id: String) = elem(id).asInstanceOf[T]
  def elem(id: String): Element = dom.document.getElementById(id)
  private def elemOpt(id: String): Option[Element] = Option(elem(id))
  def elemOptAs[T <: Element](id: String): Option[T] = elemOpt(id).map(_.asInstanceOf[T])
