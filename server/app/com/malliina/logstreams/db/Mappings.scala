package com.malliina.logstreams.db

import com.malliina.play.models.{Password, Username}
import org.joda.time.DateTime
import slick.jdbc.JdbcProfile

class Mappings(val impl: JdbcProfile) {

  import impl.api.{MappedColumnType, longColumnType, stringColumnType}

  implicit val jodaDate = MappedColumnType.base[DateTime, Long](_.getMillis, l => new DateTime(l))
  implicit val username = MappedColumnType.base[Username, String](Username.raw, Username.apply)
  implicit val password = MappedColumnType.base[Password, String](Password.raw, Password.apply)
}
