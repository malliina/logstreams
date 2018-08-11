package com.malliina.logstreams.db

import com.malliina.values.{Password, Username}

case class DataUser(username: Username, passwordHash: Password)
