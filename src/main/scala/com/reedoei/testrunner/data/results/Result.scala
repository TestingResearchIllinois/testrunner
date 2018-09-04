package com.reedoei.testrunner.data.results

sealed abstract class Result
case object Pass extends Result
case object Fail extends Result
case object Error extends Result
case object Skipped extends Result
