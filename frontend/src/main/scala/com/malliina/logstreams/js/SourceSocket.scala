package com.malliina.logstreams.js

class SourceSocket extends SocketJS("/srv?f=json"){
  override def handlePayload(payload: String): Unit = ???
}
