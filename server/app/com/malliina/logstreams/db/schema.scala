package com.malliina.logstreams.db

import com.malliina.values.{Password, Username}

case class DataUser(user: Username, passHash: Password)
case class UserToken(user: Username, token: Password)
