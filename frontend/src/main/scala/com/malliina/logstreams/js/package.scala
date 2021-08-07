package com.malliina.logstreams

import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.{DOMList, Node}

package object js {

  implicit class NodeListSeq[T <: Node](nodes: DOMList[T]) extends IndexedSeq[T] {
    override def foreach[U](f: T => U): Unit = {
      for (i <- 0 until nodes.length) {
        f(nodes(i))
      }
    }

    override def length: Int = nodes.length
    override def apply(idx: Int): T = nodes(idx)
  }

  implicit class HTMLElementOps[E <: HTMLElement](element: E) {
    def toggleClass(cls: String): Boolean = {
      val list = element.classList
      if (list.contains(cls)) list.remove(cls)
      else list.add(cls)
      false
    }

    def onClickToggleClass(cls: String): Unit =
      element.onclick = _ => toggleClass(cls)
  }

}
