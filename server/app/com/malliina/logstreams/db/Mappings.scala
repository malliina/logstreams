package com.malliina.logstreams.db

import java.time.Instant

import ch.qos.logback.classic.Level
import com.malliina.logstreams.models.{AppName, LogEntryId}
import com.malliina.play.models.{Password, Username}
import org.joda.time.DateTime
import slick.jdbc.JdbcProfile

class Mappings(val impl: JdbcProfile) {

  import impl.api.{MappedColumnType, longColumnType, stringColumnType, timestampColumnType}

  implicit val jodaDate = MappedColumnType.base[DateTime, Long](_.getMillis, l => new DateTime(l))
  implicit val username = MappedColumnType.base[Username, String](Username.raw, Username.apply)
  implicit val password = MappedColumnType.base[Password, String](Password.raw, Password.apply)
  implicit val appNameMapping = MappedColumnType.base[AppName, String](AppName.raw, AppName.apply)
  implicit val entryIdMapping = MappedColumnType.base[LogEntryId, Long](LogEntryId.raw, LogEntryId.apply)
  implicit val levelMapping = MappedColumnType.base[Level, Long](_.toInt.toLong, i => Level.toLevel(i.toInt))
  implicit val instantMapping = MappedColumnType.base[Instant, java.sql.Timestamp](java.sql.Timestamp.from, _.toInstant)
}
