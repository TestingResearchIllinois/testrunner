package com.reedoei.testrunner.runner

import java.nio.file.Path
import java.util
import java.util.concurrent.TimeUnit

import com.reedoei.testrunner.data.framework.TestFramework
import com.reedoei.testrunner.data.results.TestRunResult
import com.reedoei.testrunner.util.{ExecutionInfo, ExecutionInfoBuilder}

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

/**
  * Use this when you want to run similar sets of tests multiple times
  * It will automatically time out using information from previous runs, detect flaky tests, and
  * generally perform some basic sanity checks on the results
  *
  * Concurrency safe (if the underlying test suite can run concurrently)
  */
class SmartRunner(testFramework: TestFramework, infoStore: TestInfoStore,
                  cp: String, env: java.util.Map[String, String], outputTo: Path) extends Runner {
    // TODO: Add ability to save/load test info

    override def framework(): TestFramework = testFramework

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

    override def environment(): util.Map[String, String] = env
    override def classpath(): String = cp
    override def outputPath(): Path = outputTo
}

object SmartRunner extends RunnerProvider[SmartRunner] {
    override def withFramework(framework: TestFramework, classpath: String,
                               environment: util.Map[String, String], outputPath: Path): SmartRunner =
        new SmartRunner(framework, new TestInfoStore, classpath, environment, outputPath)
}
