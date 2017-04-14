package com.malliina.logstreams.ws

import akka.actor.{Actor, ActorRef, Terminated}
import com.malliina.logstreams.models._
import com.malliina.logstreams.ws.SourceMediatorActor.{SourceInfo, SourceJoined, SourceLeft, log}
import com.malliina.play.ws.Mediator.{Broadcast, ClientMessage}
import play.api.Logger
import play.api.libs.json.Json

object SourceMediatorActor {
  private val log = Logger(getClass)

  case class SourceInfo(source: LogSource, out: ActorRef)

  trait ControlMessage

  case class SourceJoined(source: SourceInfo) extends ControlMessage

  case class SourceLeft(source: SourceInfo) extends ControlMessage

}

class SourceMediatorActor(eventsSink: ActorRef, adminSink: ActorRef)
  extends Actor {

  var sources: Set[SourceInfo] = Set.empty

  override def preStart() = {
    updateSourceViewers()
  }

  override def receive = {
    case ClientMessage(msg, _) =>
      eventsSink ! Broadcast(msg)
    case SourceJoined(source) =>
      context watch source.out
      sources += source
      updateSourceViewers()
    case SourceLeft(source) =>
      sources -= source
      updateSourceViewers()
    case Terminated(actor) =>
      sources.find(_.out == actor) foreach { src =>
        sources -= src
        updateSourceViewers()
      }
      log info s"Sources: ${sources.size}"
  }

  def updateSourceViewers() = {
    val srcs = LogSources(sources.map(_.source).toSeq)
    val json = Json.toJson(srcs)
    adminSink ! Broadcast(json)
  }
}
