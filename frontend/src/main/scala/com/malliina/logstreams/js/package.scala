package com.malliina.logstreams

import org.scalajs.dom.{DOMList, Element, HTMLElement, Node}
import com.malliina.logstreams.models.FrontStrings.DisplayNone

package object js:
  implicit class NodeListSeq[T <: Node](nodes: DOMList[T]) extends IndexedSeq[T]:
    override def foreach[U](f: T => U): Unit =
      for i <- 0 until nodes.length do f(nodes(i))

    override def length: Int = nodes.length
    override def apply(idx: Int): T = nodes(idx)

  extension [T <: HTMLElement](element: T)
    def toggleClass(cls: String): Boolean =
      val list = element.classList
      if list.contains(cls) then list.remove(cls)
      else list.add(cls)
      false

  extension (e: Element)
    def hide(): Unit = e.classList.add(DisplayNone)
    def show(): Unit = e.classList.remove(DisplayNone)

  extension (e: HTMLElement) def hideFull(): Unit = e.className = DisplayNone
