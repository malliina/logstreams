package com.malliina.logstreams.db

import ch.qos.logback.classic.Level
import com.malliina.logstreams.models.{LogClientId, LogEntryId, LogLevel, UserAgent}
import com.malliina.values.{ErrorMessage, NonNeg, Password, Username}
import doobie.util.meta.Meta

import java.time.Instant

trait DoobieMappings:
  given Meta[Instant] = doobie.implicits.legacy.instant.JavaTimeInstantMeta
  given Meta[Username] = Meta.StringMeta.timap(Username.apply)(_.name)
  given Meta[Level] = Meta.IntMeta.timap(i => Level.toLevel(i))(_.toInt)
  given Meta[LogEntryId] = Meta.LongMeta.timap(LogEntryId.unsafe)(_.id)
  given Meta[Password] = Meta.StringMeta.timap(Password.apply)(_.pass)
  given Meta[LogLevel] = Meta.IntMeta.timap(LogLevel.unsafe)(_.int)
  given Meta[NonNeg] = Meta.IntMeta.timap(i => NonNeg(i).getUnsafe)(_.value)
  given Meta[LogClientId] = Meta.StringMeta.tiemap(LogClientId.build(_).left.map(_.message))(_.id)
  given Meta[UserAgent] = Meta.StringMeta.tiemap(UserAgent.build(_).left.map(_.message))(_.string)

extension [T](e: Either[ErrorMessage, T])
  def getUnsafe: T = e.fold(err => throw IllegalArgumentException(err.message), identity)
