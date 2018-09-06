package com.reedoei.testrunner.runner

import java.util.concurrent.TimeUnit

import com.reedoei.testrunner.data.framework.TestFramework
import com.reedoei.testrunner.util.{ExecutionInfo, ExecutionInfoBuilder}
import org.apache.maven.project.MavenProject

class SmartRunner(mavenProject: MavenProject, testFramework: TestFramework, infoStore: TestInfoStore) extends Runner {
    def timeoutFor(testOrder: Stream[String]): Long = ???

    override def execution(testOrder: Stream[String], executionInfoBuilder: ExecutionInfoBuilder): ExecutionInfo = {
        executionInfoBuilder.timeout(timeoutFor(testOrder), TimeUnit.SECONDS).build()
    }

    override def framework(): TestFramework = testFramework

    override def project(): MavenProject = mavenProject
}

object SmartRunner extends RunnerProvider[SmartRunner] {
    override def withFramework(project: MavenProject, framework: TestFramework): SmartRunner =
        new SmartRunner(project, framework, new TestInfoStore)
}
