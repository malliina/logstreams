package com.malliina.logstreams.db

import ch.qos.logback.classic.Level
import com.malliina.logstreams.models.{LogEntryId, LogLevel}
import com.malliina.values.{ErrorMessage, NonNeg, Password, Username}
import doobie.util.meta.Meta

import java.time.Instant

trait DoobieMappings:
  given Meta[Instant] = doobie.implicits.legacy.instant.JavaTimeInstantMeta
  given Meta[Username] = Meta[String].timap(Username.apply)(_.name)
  given Meta[Level] = Meta[Int].timap(i => Level.toLevel(i))(_.toInt)
  given Meta[LogEntryId] = Meta[Long].timap(LogEntryId.unsafe)(_.id)
  given Meta[Password] = Meta[String].timap(Password.apply)(_.pass)
  given Meta[LogLevel] = Meta[Int].timap(LogLevel.unsafe)(_.int)
  given Meta[NonNeg] = Meta[Int].timap(i => NonNeg(i).getUnsafe)(_.value)

extension [T](e: Either[ErrorMessage, T])
  def getUnsafe: T = e.fold(err => throw IllegalArgumentException(err.message), identity)
