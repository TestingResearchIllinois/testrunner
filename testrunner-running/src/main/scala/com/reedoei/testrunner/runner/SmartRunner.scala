package com.reedoei.testrunner.runner

import java.util.concurrent.TimeUnit

import com.reedoei.testrunner.data.framework.TestFramework
import com.reedoei.testrunner.data.results.TestRunResult
import com.reedoei.testrunner.util.{ExecutionInfo, ExecutionInfoBuilder}
import org.apache.maven.project.MavenProject

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

/**
  * Use this when you want to run similar sets of tests multiple times
  * It will automatically time out using information from previous runs, detect flaky tests, and
  * generally perform some basic sanity checks on the results
  *
  * Concurrency safe (if the underlying test suite can run concurrently)
  */
class SmartRunner(mavenProject: MavenProject, testFramework: TestFramework, infoStore: TestInfoStore) extends Runner {
    // TODO: Add ability to save/load test info

    override def framework(): TestFramework = testFramework

    override def project(): MavenProject = mavenProject

    override def run(testOrder: Stream[String]): Try[TestRunResult] = {
        val result = super.run(testOrder)

        this.synchronized(infoStore.update(testOrder.toList.asJava, result.toOption))

        // Make sure that we run exactly the set of tests that we intended to
        result.flatMap(result => {
            if (result.results().keySet().asScala.toSet == testOrder.toSet) {
                Success(result)
            } else {
                Failure(new RuntimeException("Set of executed tests is not equal to test list that should have been executed (" +
                    result.results().size() + " tests executed, " + testOrder.length + " tests expected)"))
            }
        })
    }

    override def execution(testOrder: Stream[String], executionInfoBuilder: ExecutionInfoBuilder): ExecutionInfo =
        executionInfoBuilder.timeout(timeoutFor(testOrder), TimeUnit.SECONDS).build()

    def timeoutFor(testOrder: Stream[String]): Long = infoStore.getTimeout(testOrder.toList.asJava)

    def info(): TestInfoStore = infoStore
}

object SmartRunner extends RunnerFactory[SmartRunner] {
    override def withFramework(project: MavenProject, framework: TestFramework): SmartRunner =
        new SmartRunner(project, framework, new TestInfoStore)
}
