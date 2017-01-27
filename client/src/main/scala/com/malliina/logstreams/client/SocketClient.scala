package com.malliina.logstreams.client

import java.net.URI
import java.util
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import java.util.concurrent.{Executors, TimeUnit}
import javax.net.ssl.SSLSocketFactory

import com.malliina.logstreams.client.SocketClient.log
import com.neovisionaries.ws.client._
import org.slf4j.LoggerFactory

import scala.concurrent.Promise
import scala.concurrent.duration.DurationInt

object SocketClient {
  private val log = LoggerFactory.getLogger(getClass)
}

/** A WebSocket client.
  *
  * Supports reconnects.
  */
class SocketClient(uri: URI,
                   socketFactory: SSLSocketFactory,
                   headers: Seq[KeyValue]) extends AutoCloseable {
  val connectTimeout = 20.seconds
  private val enabled = new AtomicBoolean(true)
  private val connected = new AtomicBoolean(false)
  protected val connectPromise = Promise[Unit]()
  // polls for connectivity, reconnects if necessary
  private val loopExecutor = Executors.newSingleThreadScheduledExecutor()

  val loop = loopExecutor.scheduleWithFixedDelay(new Runnable {
    override def run() = ensureConnected()
  }, 30, 30, TimeUnit.SECONDS)

  val sf = new WebSocketFactory
  sf setSSLSocketFactory socketFactory
  val socket = new AtomicReference[WebSocket](createNewSocket())

  // I think it is safe to reuse the listener across connections
  val listener = new WebSocketAdapter {
    override def onConnected(websocket: WebSocket,
                             headers: util.Map[String, util.List[String]]) = {
      log info s"Connected to ${websocket.getURI}."
      connected set true
    }

    override def onTextMessage(websocket: WebSocket, text: String) = {
      onMessage(text)
    }

    override def onDisconnected(websocket: WebSocket,
                                serverCloseFrame: WebSocketFrame,
                                clientCloseFrame: WebSocketFrame,
                                closedByServer: Boolean) = {
      log warn s"Disconnected from ${websocket.getURI}."
      connected set false
    }

    // may fire multiple times; onDisconnected fires just once
    override def onError(websocket: WebSocket, cause: WebSocketException) = {
      log.error(s"Socket ${websocket.getURI} failed.", cause)
    }
  }

  def onMessage(message: String): Unit = {}

  def isConnected: Boolean = connected.get()

  def isEnabled: Boolean = enabled.get()

  private def createNewSocket(): WebSocket = {
    val socket: WebSocket = sf.createSocket(uri, connectTimeout.toSeconds.toInt)
    headers foreach { header => socket.addHeader(header.key, header.value) }
    socket addListener listener
    socket.connectAsynchronously()
  }

  private def ensureConnected() = {
    if (isEnabled) {
      if (!isConnected) {
        log info s"Reconnecting to $uri..."
        reconnect()
      }
    } else {
      log warn s"Socket to $uri is no longer enabled."
    }
  }

  private def reconnect(): Unit = {
    killSocket(socket.get())
    socket set createNewSocket()
  }

  private def killSocket(victim: WebSocket): Unit = {
    victim removeListener listener
    victim.disconnect()
  }

  override def close() = {
    loop cancel true
    enabled set false
    killSocket(socket.get())
  }
}
