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

  def insert(events: Seq[LogEntryInput]): Future[EntriesWritten] = run {
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
  def events(query: StreamsQuery = StreamsQuery.default): Future[AppLogEvents] = run {
    val filtered = if (query.apps.isEmpty) logEntries else logEntries.filter(_.app.inSet(query.apps))
    filtered
      .sortBy(r => if (query.order == SortOrder.asc) r.added.asc else r.added.desc)
      .drop(query.offset)
      .take(query.limit)
      .sortBy(_.id.asc)
      .result
      .map(rows => AppLogEvents(rows.map(_.toEvent)))
  }
}
