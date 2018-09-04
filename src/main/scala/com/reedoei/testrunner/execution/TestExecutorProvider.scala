package com.reedoei.testrunner.execution

import com.reedoei.testrunner.data.framework.{JUnit, TestFramework}
import org.apache.maven.project.MavenProject

trait TestExecutorProvider[A <: TestExecutor] {
    def from(subject: MavenProject): A
}

object TestExecutorProvider {
    def from(project: MavenProject): Option[TestExecutor] = {
        val framework = TestFramework.testFramework(project)

        if (framework.isDefined && framework.get.eq(JUnit)) {
            Option(JUnitTestExecutor.from(project))
        } else {
            Option.empty
        }
    }
}
