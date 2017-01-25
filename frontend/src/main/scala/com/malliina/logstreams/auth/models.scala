package com.malliina.logstreams.auth

case class User(user: String)
case class Pass(token: String)
case class Creds(user: User, pass: Pass)
