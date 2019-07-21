package com.malliina.logstreams.db

import com.malliina.concurrent.Execution.cached
import com.malliina.logstreams.models._

import scala.concurrent.Future

object StreamsDatabase {
  def apply(db: StreamsSchema) = new StreamsDatabase(db)
}

class StreamsDatabase(db: StreamsSchema) {

  import db.impl.api._
  import db.logEntries
  import db.mappings._
  import db.run

  def insert(events: Seq[LogEntryInput]): Future[EntriesWritten] = run("Insert logs") {
    val action = for {
      insertedIds <- logEntries.map(_.forInsert).returning(logEntries.map(_.id)) ++= events
      insertedRows <- logEntries.filter(_.id.inSet(insertedIds)).result
    } yield EntriesWritten(events, insertedRows)
    action.transactionally
  }

  /** Always sorted by ascending ID for now.
    *
    * @param query filters
    * @return events
    */
  def events(query: StreamsQuery = StreamsQuery.default): Future[AppLogEvents] = run("Fetch logs") {
    logEntries
      .filterIf(query.apps.nonEmpty)(_.app.inSet(query.apps))
      .sortBy(r =>
        if (query.order == SortOrder.asc) (r.added.asc, r.id.asc) else (r.added.desc, r.id.asc))
      .drop(query.offset)
      .take(query.limit)
      .sortBy(_.id.asc)
      .result
      .map(rows => AppLogEvents(rows.map(_.toEvent)))
  }
}
