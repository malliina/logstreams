package com.malliina.logstreams.db

import com.malliina.concurrent.ExecutionContexts.cached
import com.malliina.logstreams.models._

import scala.concurrent.Future

object StreamsDatabase {
  def apply(db: StreamsSchema) = new StreamsDatabase(db)
}

class StreamsDatabase(db: StreamsSchema) {

  import db.impl.api._
  import db.logEntries
  import db.mappings._

  def insert(events: Seq[LogEntryInput]): Future[EntriesWritten] = {
    val action = for {
      insertedIds <- logEntries.map(_.forInsert).returning(logEntries.map(_.id)) ++= events
      insertedRows <- logEntries.filter(_.id.inSet(insertedIds)).result
    } yield EntriesWritten(events, insertedRows)
    db.run(action.transactionally)
  }

  /** Always sorted by ascending ID for now.
    *
    * @param query filters
    * @return events
    */
  def events(query: StreamsQuery = StreamsQuery.default): Future[AppLogEvents] = {
    val q = logEntries
      .sortBy(r => if (query.order == SortOrder.asc) r.added.asc else r.added.desc)
      .drop(query.offset)
      .take(query.limit)
      .sortBy(_.id.asc)
      .result
      .map(rows => AppLogEvents(rows.map(_.toEvent)))
    db.run(q)
  }
}
