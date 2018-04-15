package com.malliina.logstreams.ws

import akka.actor.{Actor, ActorRef, PoisonPill, Props, Status, Terminated}
import akka.pattern.pipe
import com.malliina.logstreams.db.StreamsDatabase
import com.malliina.logstreams.models.{AppLogEvent, AppLogEvents}
import com.malliina.logstreams.ws.LogsMediator.{NewEvents, WelcomeFailed, WelcomeMessage, log}
import com.malliina.play.ws.Mediator.{Broadcast, ClientJoined, ClientLeft, ClientMessage}
import play.api.Logger
import play.api.libs.json.Json

object LogsMediator {
  private val log = Logger(getClass)

  def props(db: StreamsDatabase) = Props(new LogsMediator(db))

  case class WelcomeMessage(events: Seq[AppLogEvent], to: ActorRef)

  case class WelcomeFailed(t: Throwable, to: ActorRef)

  case class NewEvents(events: Seq[AppLogEvent])

}

class LogsMediator(db: StreamsDatabase) extends Actor {
  var clients: Set[ActorRef] = Set.empty
  // Clients which have not yet received the welcome event
  var pending: Map[ActorRef, Seq[AppLogEvent]] = Map.empty
  implicit val ex = context.dispatcher

  override def receive: Receive = {
    case NewEvents(events) =>
      val oldPending = pending
      oldPending.foreach { case (ref, buffer) =>
        val newBuffer = buffer ++ events
        if (newBuffer.size > 10000) {
          pending -= ref
          ref ! PoisonPill
        } else {
          pending += ref -> (buffer ++ events)
        }
      }
      val json = Json.toJson(AppLogEvents(events))
      clients foreach { out => out ! json }
    case Broadcast(message) =>
      clients foreach { out => out ! message }
    case ClientMessage(_, _) =>
    //      onClientMessage(message, rh)
    case ClientJoined(ref) =>
      context watch ref
      pending += ref -> Nil
      val welcomeTask = db.events()
        .map { es => WelcomeMessage(es.events, ref) }
        .recover { case t => WelcomeFailed(t, ref) }
      welcomeTask.pipeTo(self)(sender())
    case WelcomeMessage(events, to) =>
      val oldPending = pending
      oldPending.find(_._1 == to).foreach { case (ref, buffer) =>
        pending -= ref
        clients += ref
        if (events.nonEmpty)
          ref ! Json.toJson(AppLogEvents(deduplicate(events, buffer)))
      }
    case WelcomeFailed(t, to) =>
      log.error(s"Failed to load log entries.", t)
      pending -= to
      to ! PoisonPill
    case ClientLeft(ref) =>
      clients -= ref
      pending -= ref
    case Terminated(ref) =>
      clients -= ref
      pending -= ref
    case Status.Failure(t) =>
      // Received if the Future used with pipeTo fails
      log.error(s"Failed to load log entries.", t)
  }

  def deduplicate(events: Seq[AppLogEvent], buffer: Seq[AppLogEvent]): Seq[AppLogEvent] = {
    if (buffer.isEmpty) events
    else (events ++ buffer.filterNot(b => events.exists(_.id == b.id))).sortBy(_.id)
  }
}
