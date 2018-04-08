package com.malliina.logstreams.ws

import akka.actor.{Actor, ActorRef, Terminated}
import com.malliina.logstreams.models._
import com.malliina.logstreams.ws.SourceMediator.{SourceInfo, SourceJoined, SourceLeft, log}
import com.malliina.play.ws.Mediator.{Broadcast, ClientMessage}
import play.api.Logger
import play.api.libs.json.Json

object SourceMediator {
  private val log = Logger(getClass)

  case class SourceInfo(source: LogSource, out: ActorRef)

  trait ControlMessage

  case class SourceJoined(source: SourceInfo) extends ControlMessage

  case class SourceLeft(source: SourceInfo) extends ControlMessage

}

class SourceMediator(logViewers: ActorRef, sourceViewers: ActorRef, database: ActorRef)
  extends Actor {

  var sources: Set[SourceInfo] = Set.empty

  override def preStart(): Unit = {
    updateSourceViewers()
  }

  override def receive: Receive = {
    case AppLogEvents(events) =>
      database ! AppLogEvents(events)
      logViewers ! Broadcast(Json.toJson(AppLogEvents(events)))
    case ClientMessage(msg, _) =>
      logViewers ! Broadcast(msg)
    case SourceJoined(source) =>
      context watch source.out
      sources += source
      log info s"Source '${source.source.name}' from '${source.source.remoteAddress}' joined."
      updateSourceViewers()
    case SourceLeft(source) =>
      remove(source)
    case Terminated(actor) =>
      sources.find(_.out == actor) foreach { src =>
        remove(src)
      }
      log info s"Sources left: ${sources.size}."
  }

  private def remove(source: SourceInfo) = {
    sources -= source
    log info s"Source '${source.source.name}' from '${source.source.remoteAddress}' left."
    updateSourceViewers()
  }

  def updateSourceViewers() = {
    val srcs = LogSources(sources.map(_.source).toSeq)
    val json = Json.toJson(srcs)
    sourceViewers ! Broadcast(json)
  }
}
