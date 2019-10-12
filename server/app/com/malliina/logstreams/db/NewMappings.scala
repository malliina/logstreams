package com.malliina.logstreams.db

import java.time.Instant
import java.util.Date

import ch.qos.logback.classic.Level
import com.malliina.logstreams.models.LogEntryId
import io.getquill.MappedEncoding

object NewMappings {
  implicit val instantDecoder = MappedEncoding[Date, Instant](d => d.toInstant)
  implicit val instantEncoder = MappedEncoding[Instant, Date](i => Date.from(i))

  implicit val entryIdDecoder = MappedEncoding[Long, LogEntryId](LogEntryId.apply)
  implicit val entryIdEncoder = MappedEncoding[LogEntryId, Long](LogEntryId.raw)

  implicit val levelDecoder = MappedEncoding[Long, Level](i => Level.toLevel(i.toInt))
  implicit val levelEncoder = MappedEncoding[Level, Long](_.toInt.toLong)
}
