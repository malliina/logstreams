package com.malliina.logstreams

import ch.qos.logback.classic.Level
import com.malliina.logbackrx.LogEvent
import com.malliina.logstreams.models.{AppLogEvent, AppName, LogSource}

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

  def failEvent(msg: String) = {
    LogEvent(
      System.currentTimeMillis(),
      "now!",
      msg,
      getClass.getName.stripSuffix("$"),
      Thread.currentThread().getName,
      Level.ERROR,
      Option(new Exception("boom").getStackTraceString)
    )
  }

  def testEvent(e: LogEvent) = AppLogEvent(LogSource(AppName("test"), "localhost"), e)
}
