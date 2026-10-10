package com.malliina.logstreams.db

import cats.syntax.all.{catsSyntaxApplicativeId, catsSyntaxList}
import com.malliina.database.DoobieDatabase
import com.malliina.logstreams.db.DoobieLogsDatabase.log
import com.malliina.logstreams.models.*
import com.malliina.util.AppLogger
import doobie.syntax.all.toSqlInterpolator
import doobie.{ConnectionIO, Fragment, Fragments, Update}

object DoobieLogsDatabase:
  private val log = AppLogger(getClass)

class DoobieLogsDatabase[F[_]](db: DoobieDatabase[F]) extends LogsDatabase[F] with DoobieMappings:
  def insert(events: List[LogEntryInput]): F[EntriesWritten] = db.run:
    val q =
      """insert into LOGS(APP, ADDRESS, TIMESTAMP, MESSAGE, LOGGER, THREAD, LEVEL, CLIENT, USER_AGENT, STACKTRACE)
         values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
    Update[LogEntryInput](q)
      .updateManyWithGeneratedKeys[LogEntryId]("ID")(events)
      .compile
      .toList
      .flatMap: (idList: List[LogEntryId]) =>
        idList.toNel
          .map: nel =>
            val inClause = Fragments.in(fr"ID", nel)
            logsQuery(inClause)
          .getOrElse:
            List.empty[LogEntryRow].pure[ConnectionIO]
          .map: list =>
            EntriesWritten(events, list)

  def events(query: StreamsQuery = StreamsQuery.default): F[AppLogEvents] = db.run:
    val levels = LogLevel.all
      .filter(l => l.int >= query.level.int)
      .toList
      .map(_.int)
      .toNel
    log.debug(s"Query with $query using levels $levels")
    val whereClause = Fragments
      .andOpt(
        query.apps.toList.toNel.map(apps => Fragments.in(fr"APP", apps)),
        levels.map(ls => Fragments.in(fr"LEVEL", ls)),
        query.queryStar
          .map(q => fr"MATCH(APP, ADDRESS, MESSAGE, LOGGER, THREAD, STACKTRACE) AGAINST($q)"),
        query.timeRange.from.map(f => fr"ADDED >= $f"),
        query.timeRange.to.map(t => fr"ADDED <= $t")
      )
      .getOrElse(fr"true")
    val order = if query.order == SortOrder.asc then fr0"asc" else fr0"desc"
    val where =
      fr"$whereClause order by ADDED $order, ID $order limit ${query.limit} offset ${query.offset}"
    logsQuery(where)
      .map: rows =>
        AppLogEvents(rows.map(_.toEvent))

  private def logsQuery(where: Fragment) =
    sql"""select ID, APP, ADDRESS, TIMESTAMP, MESSAGE, LOGGER, THREAD, LEVEL, CLIENT, USER_AGENT, STACKTRACE, ADDED
          from LOGS
          where $where"""
      .query[LogEntryRow]
      .to[List]
