package com.malliina.logstreams

import java.time.Instant

import ch.qos.logback.classic.Level
import com.malliina.logbackrx.LogEvent
import com.malliina.logstreams.models._

object TestData {
  // Dev purposes

  def dummyEvent(msg: String) = LogEvent(
    System.currentTimeMillis(),
    "now",
    msg,
    getClass.getName.stripSuffix("$"),
    "this thread",
    Level.INFO,
    None)

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
    LogEntryId(0),
    LogSource(AppName("test"), "localhost"),
    e,
    Instant.now().toEpochMilli,
    LogEntryRow.format(Instant.now())
  )
}
