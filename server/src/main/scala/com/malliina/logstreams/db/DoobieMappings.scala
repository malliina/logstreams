package com.malliina.logstreams.db

import java.time.Instant
import ch.qos.logback.classic.Level
import com.malliina.logstreams.models.{LogEntryId, LogLevel}
import com.malliina.values.{Password, Username}
import doobie.util.meta.Meta

trait DoobieMappings:
  implicit val im: Meta[Instant] = doobie.implicits.legacy.instant.JavaTimeInstantMeta
  implicit val um: Meta[Username] = Meta[String].timap(Username.apply)(_.name)
  implicit val lm: Meta[Level] = Meta[Int].timap(i => Level.toLevel(i))(_.toInt)
  implicit val lem: Meta[LogEntryId] = Meta[Long].timap(LogEntryId.apply)(_.id)
  implicit val pm: Meta[Password] = Meta[String].timap(Password.apply)(_.pass)
  implicit val ll: Meta[LogLevel] = Meta[Int].timap(parseLevel)(_.int)

  private def parseLevel(i: Int): LogLevel = LogLevel.of(i).getOrElse(legacyIntLevel(i))

  private def legacyIntLevel(i: Int): LogLevel = i match
    case Level.TRACE_INT => LogLevel.Trace
    case Level.DEBUG_INT => LogLevel.Debug
    case Level.INFO_INT  => LogLevel.Info
    case Level.WARN_INT  => LogLevel.Warn
    case Level.ERROR_INT => LogLevel.Error
    case _               => LogLevel.Other
