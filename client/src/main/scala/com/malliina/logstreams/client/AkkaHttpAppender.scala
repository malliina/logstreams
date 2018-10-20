package com.malliina.logstreams.client

import com.malliina.http.FullUrl

class AkkaHttpAppender extends SocketAppender[WebSocketClient] {
  override def start(): Unit = {
    if (getEnabled) {
      val result = for {
        hostAndPort <- toMissing(endpoint, "endpoint")
        user <- toMissing(username, "username")
        pass <- toMissing(password, "password")
        _ <- validate(hostAndPort).right
      } yield {
        val headers: List[KeyValue] = List(HttpUtil.basicAuth(user, pass))
        val scheme = if (getSecure) "wss" else "ws"
        val uri = FullUrl(scheme, hostAndPort, "/ws/sources")
        addInfo(s"Connecting to logstreams URL '$uri' with Akka Streams for Logback...")
        //        val socket = new JsonSocket(uri, sf, headers)
        val webSocket = WebSocketClient(uri, headers, as, mat)
        client = Option(webSocket)
        val task = webSocket.connect(logEvents.map(e => LogEvents(Seq(e))))
        task.onComplete { t =>
          t.fold(err => addError(s"Appender [$name] failed.", err),
            _ => addError(s"Appender [$name] completed."))
        }
        super.start()
      }
      result.left.toOption foreach addError
    } else {
      addInfo("Logstreams client is disabled.")
    }
  }
}
