package com.malliina.logstreams.db

import cats.data.NonEmptyList
import cats.implicits._
import com.malliina.logstreams.models._
import doobie._
import doobie.implicits._

import scala.concurrent.Future

object DoobieStreamsDatabase {
  def apply(db: DoobieDatabase): DoobieStreamsDatabase = new DoobieStreamsDatabase(db)
}

class DoobieStreamsDatabase(db: DoobieDatabase) extends LogsDatabase {
  def insert(events: List[LogEntryInput]): Future[EntriesWritten] = db.run {
    val insertions = events.traverse { e =>
      sql"""insert into LOGS(APP, ADDRESS, MESSAGE, LOGGER, THREAD, LEVEL, STACKTRACE, TIMESTAMP) 
            values(${e.appName}, ${e.remoteAddress}, ${e.message}, ${e.loggerName}, ${e.threadName}, ${e.level}, ${e.stackTrace}, ${e.timestamp})""".update
        .withUniqueGeneratedKeys[LogEntryId]("ID")
    }
    insertions.flatMap { idList =>
      toNonEmpty(idList)
        .map { ids =>
          val inClause = Fragments.in(fr"ID", ids)
          sql"""select ID, APP, ADDRESS, TIMESTAMP, MESSAGE, LOGGER, THREAD, LEVEL, STACKTRACE, ADDED
                from LOGS 
                where $inClause""".query[LogEntryRow].to[List]
        }
        .getOrElse {
          AsyncConnectionIO.pure(Nil)
        }
        .map { list =>
          EntriesWritten(events, list)
        }
    }
  }

  def events(query: StreamsQuery = StreamsQuery.default): Future[AppLogEvents] = db.run {
    val whereClause =
      Fragments.whereAndOpt(toNonEmpty(query.apps.toList).map(apps => Fragments.in(fr"APP", apps)))
    val order = if (query.order == SortOrder.asc) fr0"asc" else fr0"desc"
    sql"""select ID, APP, ADDRESS, TIMESTAMP, MESSAGE, LOGGER, THREAD, LEVEL, STACKTRACE, ADDED
          from LOGS $whereClause 
          order by ADDED $order, ID $order
          limit ${query.limit} offset ${query.offset}""".query[LogEntryRow].to[List].map { rows =>
      AppLogEvents(rows.map(_.toEvent))
    }
  }

  def toNonEmpty[T](ts: List[T]): Option[NonEmptyList[T]] = ts match {
    case t :: head => Option(NonEmptyList(t, head))
    case Nil       => None
  }
}
