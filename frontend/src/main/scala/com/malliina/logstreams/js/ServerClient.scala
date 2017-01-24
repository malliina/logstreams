package com.malliina.logstreams.js

class ServerClient extends SocketJS("/srv?f=json"){
  override def handlePayload(payload: String): Unit = ???
}
