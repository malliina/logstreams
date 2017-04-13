package com.malliina.logstreams.db

import com.malliina.play.models.{Password, Username}
import org.joda.time.DateTime
import slick.jdbc.H2Profile.api.{MappedColumnType, longColumnType, stringColumnType}

object Mappings {
  implicit val jodaDate = MappedColumnType.base[DateTime, Long](_.getMillis, l => new DateTime(l))
  implicit val username = MappedColumnType.base[Username, String](Username.raw, Username.apply)
  implicit val password = MappedColumnType.base[Password, String](Password.raw, Password.apply)
}
