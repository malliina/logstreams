package com.malliina.logstreams.js

import org.scalajs.dom

trait Settings:
  def isVerbose: Boolean
  def saveVerbose(newVerbose: Boolean): Unit

object StorageSettings extends Settings:
  private val VerboseKey = "verbose"

  val localStorage = dom.window.localStorage

  def isVerbose: Boolean =
    Option(localStorage.getItem(VerboseKey)).contains("true")
  def saveVerbose(newVerbose: Boolean): Unit =
    localStorage.setItem(VerboseKey, if newVerbose then "true" else "false")
