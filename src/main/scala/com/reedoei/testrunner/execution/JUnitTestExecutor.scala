package com.reedoei.testrunner.execution

import com.reedoei.testrunner.data.results.TestRunResult
import org.apache.maven.project.MavenProject

class JUnitTestExecutor(project: MavenProject) extends TestExecutor {
    override def run(testOrder: Stream[String]): TestRunResult = ???
}

object JUnitTestExecutor extends TestExecutorProvider[JUnitTestExecutor] {
    override def from(project: MavenProject): JUnitTestExecutor = new JUnitTestExecutor(project)
}
