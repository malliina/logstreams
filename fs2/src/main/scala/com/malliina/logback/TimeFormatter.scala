package com.malliina.logback

import ch.qos.logback.core.CoreConstants
import ch.qos.logback.core.util.CachingDateFormatter
import com.malliina.logback.TimeFormatter.helsinki

import java.time.ZoneId

/** @param simpleDateFormat
  *   time format
  * @see
  *   ch.qos.logback.classic.pattern.DateConverter.java
  */
class TimeFormatter(simpleDateFormat: String):
  val (timeFormat, formatter) =
    val specifiedFormat =
      if simpleDateFormat == CoreConstants.ISO8601_STR then CoreConstants.ISO8601_PATTERN
      else simpleDateFormat
    try (specifiedFormat, new CachingDateFormatter(specifiedFormat, helsinki))
    catch
      case _: IllegalArgumentException =>
        (
          CoreConstants.ISO8601_PATTERN,
          new CachingDateFormatter(CoreConstants.ISO8601_PATTERN, helsinki)
        )

  def format(timeStamp: Long): String = formatter format timeStamp

object TimeFormatter:
  val helsinki = ZoneId.of("Europe/Helsinki")
