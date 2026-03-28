package com.malliina.logstreams.js

import org.scalajs.dom.{HTMLFormElement, document}

class ProfilePage(val log: BaseLogger = BaseLogger.printer) extends ScriptHelpers:
  document
    .getElementsByName(LanguageKey)
    .foreach: radio =>
      radio.addOnClick: e =>
        getElem[HTMLFormElement](LanguageForm).submit()
