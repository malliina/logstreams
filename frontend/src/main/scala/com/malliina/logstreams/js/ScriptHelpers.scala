package com.malliina.logstreams.js

import org.scalajs.jquery.{JQuery, jQuery}

trait ScriptHelpers {
  def elem(id: String): JQuery = jQuery(s"#$id")
}
