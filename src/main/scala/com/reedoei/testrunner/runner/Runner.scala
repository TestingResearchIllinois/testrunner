package com.reedoei.testrunner.runner

import com.reedoei.eunomia.subject.Subject
import com.reedoei.testrunner.data.results.TestRunResult

trait Runner {
    def run(subject: Subject, testOrder: List[String]): Option[TestRunResult]
}

object Runner {

}
