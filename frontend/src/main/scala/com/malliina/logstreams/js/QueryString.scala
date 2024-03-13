package com.malliina.logstreams.js

import org.scalajs.dom.{URLSearchParams, window}

object QueryString:
  def parse = QueryString(URLSearchParams(window.location.search))

class QueryString(val inner: URLSearchParams):
  def get(name: String) = Option(inner.get(name))
  def getAll(name: String) = Option(inner.getAll(name)).map(_.toList).getOrElse(Nil)
  def contains(name: String) = get(name).nonEmpty
  def set(name: String, value: String): Unit = inner.set(name, value)
  def append(name: String, value: String): Unit = inner.append(name, value)
  def delete(name: String): Unit = inner.delete(name)
  def setOrDelete(name: String, value: Option[String]): Unit =
    value.map(v => set(name, v)).getOrElse(delete(name))
  def render = inner.toString
  def isEmpty = inner.isEmpty
  override def toString: String = render
  def commit(): Unit = window.history.replaceState("", "", s"${window.location.pathname}?$render")
