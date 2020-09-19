package com.malliina.logstreams.db

import com.malliina.logstreams.models.{AppLogEvents, EntriesWritten, LogEntryInput}

import scala.concurrent.Future

trait LogsDatabase {
  def insert(events: List[LogEntryInput]): Future[EntriesWritten]
  def events(query: StreamsQuery = StreamsQuery.default): Future[AppLogEvents]
}
