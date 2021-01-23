package com.malliina.logstreams.db

import com.malliina.logstreams.models.{AppLogEvents, EntriesWritten, LogEntryInput}

trait LogsDatabase[F[_]] {
  def insert(events: List[LogEntryInput]): F[EntriesWritten]
  def events(query: StreamsQuery = StreamsQuery.default): F[AppLogEvents]
}
