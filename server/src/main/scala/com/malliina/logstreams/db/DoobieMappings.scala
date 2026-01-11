package com.malliina.logstreams.db

import cats.Show
import ch.qos.logback.classic.Level
import com.malliina.logstreams.models.{AppName, LogClientId, LogEntryId, LogLevel, UserAgent}
import com.malliina.values.{ErrorMessage, NonNeg, Password, Username, ValidatingCompanion}
import doobie.util.meta.Meta

import java.time.Instant

trait DoobieMappings:
  given Meta[Instant] = doobie.implicits.legacy.instant.JavaTimeInstantMeta
  given Meta[Username] = validated(Username)
  given Meta[AppName] = validated(AppName)
  given Meta[Level] = Meta.IntMeta.timap(i => Level.toLevel(i))(_.toInt)
  given Meta[LogEntryId] = validated(LogEntryId)
  given Meta[Password] = validated(Password)
  given Meta[LogLevel] =
    Meta.IntMeta.tiemap(int => LogLevel.of(int).toRight(s"Invalid level: '$int'."))(_.int)
  given Meta[NonNeg] = validated(NonNeg)
  given Meta[LogClientId] = validated(LogClientId)
  given Meta[UserAgent] = validated(UserAgent)

  private def validated[T, R: {Meta, Show}, C <: ValidatingCompanion[R, T]](c: C): Meta[T] =
    Meta[R].tiemap(r => c.build(r).left.map(err => err.message))(c.write)

extension [T](e: Either[ErrorMessage, T])
  def getUnsafe: T = e.fold(err => throw IllegalArgumentException(err.message), identity)
