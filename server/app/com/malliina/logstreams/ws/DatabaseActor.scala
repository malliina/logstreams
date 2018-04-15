package com.malliina.logstreams.ws

import akka.actor.{Actor, Props, Status}
import akka.pattern.pipe
import com.malliina.logstreams.db.StreamsDatabase
import com.malliina.logstreams.models.{EntriesWritten, LogEntryInputs}
import com.malliina.logstreams.ws.DatabaseActor.log
import com.malliina.logstreams.ws.SourceMediator.EventsWritten
import play.api.Logger

object DatabaseActor {
  private val log = Logger(getClass)

  def props(db: StreamsDatabase): Props = Props(new DatabaseActor(db))
}

class DatabaseActor(db: StreamsDatabase) extends Actor {
  implicit val ex = context.dispatcher

  override def receive: Receive = {
    case LogEntryInputs(events) =>
      db.insert(events).pipeTo(self)(sender())
    case EntriesWritten(inputs, rows) =>
      log.debug(s"Wrote ${rows.length} rows for ${inputs.length} entries.")
      sender() ! EventsWritten(rows)
    case Status.Failure(t) =>
      // Received if the Future used with pipeTo fails
      log.error(s"Failed to save log entries.", t)
  }
}
