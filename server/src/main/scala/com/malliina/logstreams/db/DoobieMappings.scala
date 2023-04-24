package com.malliina.logstreams.db

import ch.qos.logback.classic.Level
import com.malliina.logstreams.models.{LogEntryId, LogLevel}
import com.malliina.values.{Password, Username}
import doobie.util.meta.Meta

import java.time.Instant

trait DoobieMappings:
  implicit val im: Meta[Instant] = doobie.implicits.legacy.instant.JavaTimeInstantMeta
  implicit val um: Meta[Username] = Meta[String].timap(Username.apply)(_.name)
  implicit val lm: Meta[Level] = Meta[Int].timap(i => Level.toLevel(i))(_.toInt)
  implicit val lem: Meta[LogEntryId] = Meta[Long].timap(LogEntryId.apply)(_.id)
  implicit val pm: Meta[Password] = Meta[String].timap(Password.apply)(_.pass)
  implicit val ll: Meta[LogLevel] = Meta[Int].timap(LogLevel.unsafe)(_.int)
