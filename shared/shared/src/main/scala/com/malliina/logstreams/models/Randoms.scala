package com.malliina.logstreams.models

object Randoms:
  private val chars = "abcdefghijklmnopqrstuvwxyz0123456789"

  def randomString(length: Int): String =
    (1 to length).map(_ => randomChar()).mkString

  def randomChar(): Char =
    chars.charAt(math.floor(math.random() * chars.length).toInt)
