package com.reedoei.testrunner.runner

import com.reedoei.eunomia.subject.Subject
import com.reedoei.testrunner.execution.TestExecutorProvider
import com.reedoei.testrunner.data.results.TestRunResult

class SmartRunner extends Runner {
    override def run(subject: Subject, testOrder: List[String]): Option[TestRunResult] =
        TestExecutorProvider.withSubject(subject).map(e => e.run(testOrder))
}
