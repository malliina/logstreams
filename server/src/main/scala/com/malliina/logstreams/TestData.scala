package com.malliina.logstreams

import java.time.Instant
import ch.qos.logback.classic.Level
import com.malliina.logback.LogEvent
import com.malliina.logstreams.models.*
import com.malliina.values.Username

object TestData:
  // Dev purposes

  def dummyEvent(msg: String) = LogEvent(
    System.currentTimeMillis(),
    "now",
    msg,
    getClass.getName.stripSuffix("$"),
    "this thread",
    Level.INFO,
    None
  )

  def failEvent(msg: String) = LogEvent(
    System.currentTimeMillis(),
    "now!",
    msg,
    getClass.getName.stripSuffix("$"),
    Thread.currentThread().getName,
    Level.ERROR,
    Option("Test stack string")
  )

  def testEvent(e: models.LogEvent) = AppLogEvent(
    LogEntryId.build(0).toOption.get,
    SimpleLogSource(AppName.fromUsername(Username("test")), "localhost"),
    e,
    Instant.now().toEpochMilli,
    LogEntryRow.format(Instant.now())
  )
