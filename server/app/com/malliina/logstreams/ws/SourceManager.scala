package com.malliina.logstreams.ws

import akka.actor.{Actor, ActorRef, Props}
import com.malliina.logstreams.models.{LogSource, LogSources}
import com.malliina.logstreams.ws.SourceManager.{AppJoined, AppLeft, GetApps, log}
import play.api.Logger

object SourceManager {
  private val log = Logger(getClass)

  def props(sourcesOut: ActorRef) = Props(new SourceManager(sourcesOut))

  case class AppJoined(source: LogSource)
  case class AppLeft(source: LogSource)
  case object GetApps
}

/** Keeps track of currently connected servers.
  *
  * @param sourcesOut sink for updates
  */
class SourceManager(sourcesOut: ActorRef) extends Actor {

  var sources: Set[LogSource] = Set.empty

  override def receive: Receive = {
    case AppJoined(source) =>
      sources += source
      log info s"Source '${source.name}' from '${source.remoteAddress}' joined."
      updateSourceViewers()
    case AppLeft(source) =>
      sources.find(_ == source).foreach { source =>
        sources -= source
        log info s"Source '${source.name}' from '${source.remoteAddress}' left."
        updateSourceViewers()
      }
      log info s"Sources left: ${sources.size}."
    case GetApps =>
      sender() ! apps
  }

  def apps = LogSources(sources.toSeq)

  def updateSourceViewers(): Unit = {
    sourcesOut ! apps
  }
}
