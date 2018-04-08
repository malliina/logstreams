package com.malliina.logstreams.ws

import akka.actor.{Actor, Props, Status}
import akka.pattern.pipe
import com.malliina.logstreams.db.StreamsDB
import com.malliina.logstreams.models.{AppLogEvent, AppLogEvents}
import com.malliina.logstreams.ws.DatabaseActor.{EntriesWritten, log}
import play.api.Logger

object DatabaseActor {
  private val log = Logger(getClass)

  def props(db: StreamsDB): Props = Props(new DatabaseActor(db))

  case class EntriesWritten(rows: Int, input: Seq[AppLogEvent])

}

class DatabaseActor(db: StreamsDB) extends Actor {

  import db.impl.api._

  implicit val ex = context.dispatcher

  override def receive: Receive = {
    case AppLogEvents(events) =>
      val action = db.logEntries.map(_.forInsert) ++= events.map(_.toInput)
      db.run(action)
        .map(maybeInt => EntriesWritten(maybeInt.getOrElse(0), events))
        .pipeTo(self)(sender())
    case EntriesWritten(rows, input) =>
      log.debug(s"Wrote $rows rows for ${input.length} entries.")
    case Status.Failure(t) =>
      // Received if the Future used with pipeTo fails
      log.error(s"Failed to save log entries.", t)
  }
}
