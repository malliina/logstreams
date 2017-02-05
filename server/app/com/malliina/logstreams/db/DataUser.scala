package com.malliina.logstreams.db

import com.malliina.play.models.{Password, Username}

case class DataUser(username: Username, passwordHash: Password)
