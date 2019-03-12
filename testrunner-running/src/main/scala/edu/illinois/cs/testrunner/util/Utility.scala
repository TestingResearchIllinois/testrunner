package edu.illinois.cs.testrunner.util

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId}

object Utility {
    def timestamp(): String =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss").format(Instant.now().atZone(ZoneId.systemDefault()))
}
