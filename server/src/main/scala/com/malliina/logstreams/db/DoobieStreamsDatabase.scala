package com.malliina.logstreams.db

import cats.effect.IO
import cats.implicits._
import com.malliina.logstreams.models._
import com.malliina.util.AppLogger
import doobie._
import doobie.implicits._
import DoobieStreamsDatabase.log
import ch.qos.logback.classic.Level

object DoobieStreamsDatabase {
  private val log = AppLogger(getClass)

  def apply(db: DoobieDatabase): DoobieStreamsDatabase = new DoobieStreamsDatabase(db)
}

class DoobieStreamsDatabase(db: DoobieDatabase) extends LogsDatabase[IO] {
  implicit val dbLog = db.logHandler

  def insert(events: List[LogEntryInput]): IO[EntriesWritten] = db.run {
    val insertions = events.traverse { e =>
      sql"""insert into LOGS(APP, ADDRESS, MESSAGE, LOGGER, THREAD, LEVEL, STACKTRACE, TIMESTAMP) 
            values(${e.appName}, ${e.remoteAddress}, ${e.message}, ${e.loggerName}, ${e.threadName}, ${e.level}, ${e.stackTrace}, ${e.timestamp})""".update
        .withUniqueGeneratedKeys[LogEntryId]("ID")
    }
    insertions.flatMap { idList =>
      idList.toNel
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

  def events(query: StreamsQuery = StreamsQuery.default): IO[AppLogEvents] = db.run {
    val levels = LogLevel.all
      .filter(l => l.int >= query.level.int)
      .toList
      .flatMap(l => Seq(l.int, toLogback(l).toInt))
      .toNel
    log.info(s"Query with $query using levels $levels")
    val whereClause =
      Fragments.whereAndOpt(
        query.apps.toList.toNel.map(apps => Fragments.in(fr"APP", apps)),
        levels.map(ls => Fragments.in(fr"LEVEL", ls))
      )
    val order = if (query.order == SortOrder.asc) fr0"asc" else fr0"desc"
    sql"""select ID, APP, ADDRESS, TIMESTAMP, MESSAGE, LOGGER, THREAD, LEVEL, STACKTRACE, ADDED
          from LOGS $whereClause 
          order by ADDED $order, ID $order
          limit ${query.limit} offset ${query.offset}""".query[LogEntryRow].to[List].map { rows =>
      AppLogEvents(rows.map(_.toEvent))
    }
  }

  // Legacy, TODO rename old levels with e.g. DB migration
  private def toLogback(l: LogLevel): Level = l match {
    case LogLevel.Trace => Level.TRACE
    case LogLevel.Debug => Level.DEBUG
    case LogLevel.Info  => Level.INFO
    case LogLevel.Warn  => Level.WARN
    case LogLevel.Error => Level.ERROR
    case LogLevel.Other => Level.OFF
  }
}
