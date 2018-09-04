package com.reedoei.testrunner.execution

import com.reedoei.testrunner.data.results.TestRunResult

trait TestExecutor {
    def run(testOrder: Stream[String]): TestRunResult
}
