package com.malliina.logback

import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.{Level, Logger, LoggerContext}
import ch.qos.logback.core.{Appender, ConsoleAppender}
import org.slf4j.LoggerFactory

object LogbackUtils:
  def init(
    pattern: String = """%d{HH:mm:ss.SSS} %-5level %logger{72} %msg%n""",
    rootLevel: Level = Level.INFO,
    levelsByLogger: Map[String, Level] = Map.empty
  ): LoggerContext =
    val lc = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    lc.reset()
    val ple = PatternLayoutEncoder()
    ple.setPattern(pattern)
    ple.setContext(lc)
    ple.start()
    val console = new ConsoleAppender[ILoggingEvent]()
    console.setEncoder(ple)
    LogbackUtils.installAppender(console)
    val root = lc.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
    root.setLevel(rootLevel)
    levelsByLogger.foreach { (loggerName, level) =>
      lc.getLogger(loggerName).setLevel(level)
    }
    lc

  def appender[T](
    appenderName: String,
    loggerName: String = org.slf4j.Logger.ROOT_LOGGER_NAME
  ): Option[T] =
    Option(
      LoggerFactory
        .getLogger(loggerName)
        .asInstanceOf[Logger]
        .getAppender(appenderName)
        .asInstanceOf[T]
    )

  def getAppender[T](appenderName: String, loggerName: String = "ROOT"): T =
    appender[T](appenderName, loggerName)
      .getOrElse(
        throw new NoSuchElementException(
          s"Unable to find appender with name: $appenderName"
        )
      )

  def installAppender(
    appender: Appender[ILoggingEvent],
    loggerName: String = org.slf4j.Logger.ROOT_LOGGER_NAME
  ): Unit =
    if appender.getContext == null then
      appender setContext LoggerFactory.getILoggerFactory
        .asInstanceOf[LoggerContext]
    if !appender.isStarted then appender.start()
    val logger = LoggerFactory.getLogger(loggerName).asInstanceOf[Logger]
    logger addAppender appender
