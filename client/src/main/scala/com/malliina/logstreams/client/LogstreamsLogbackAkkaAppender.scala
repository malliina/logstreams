package com.malliina.logstreams.client

import akka.stream.scaladsl.Sink
import com.malliina.http.FullUrl
import com.malliina.logback.LogEvent

class LogstreamsLogbackAkkaAppender extends SocketAppender[JsonSocket] {
  override def start(): Unit = {
    if (getEnabled) {
      val result = for {
        hostAndPort <- toMissing(endpoint, "endpoint")
        user <- toMissing(username, "username")
        pass <- toMissing(password, "password")
        _ <- validate(hostAndPort)
      } yield {
        val headers: Seq[KeyValue] = Seq(HttpUtil.basicAuth(user, pass))
        val host = hostAndPort.takeWhile(_ != ':')
        val sf = CustomSSLSocketFactory.forHost(host)
        val scheme = if (getSecure) "wss" else "ws"
        val uri = FullUrl(scheme, hostAndPort, "/ws/sources")
        addInfo(s"Connecting to logstreams URL '$uri' with Akka Streams for Logback...")
        val socket = new JsonSocket(uri, sf, headers)
        client = Option(socket)
        val socketSink = Sink.foreach[LogEvent] { event =>
          socket.sendMessage(LogEvents(Seq(event)))
        }
        val task = logEvents.runWith(socketSink)
        task.onComplete { t =>
          t.fold(
            err => addError(s"Appender [$name] failed.", err),
            _ => addError(s"Appender [$name] completed.")
          )
        }
        super.start()
      }
      result.left.toOption foreach addError
    } else {
      addInfo("Logstreams client is disabled.")
    }
  }
}
