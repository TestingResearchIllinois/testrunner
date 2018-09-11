package com.reedoei.testrunner.data.results

case class TestResult(name: String, result: Result, time: Double, stackTrace: Array[StackTraceElement])
