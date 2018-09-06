package com.reedoei.testrunner.runner

import com.google.gson.Gson
import com.reedoei.testrunner.configuration.Configuration
import com.reedoei.testrunner.data.framework.TestFramework
import com.reedoei.testrunner.data.results.TestRunResult
import com.reedoei.testrunner.execution.Executor
import com.reedoei.testrunner.util._
import org.apache.maven.project.MavenProject

import scala.io.Source
import scala.util.{Failure, Try}

trait Runner {
    def run(testOrder: Stream[String]): Option[TestRunResult] =
        TempFiles.withSeq(testOrder)(path => TempFiles.withTempFile(outputPath => {
            val cp = new MavenClassLoader(project()).classpath()

            val builder = new ExecutionInfoBuilder(Executor.getClass).classpath(cp)

            val exitCode =
                execution(testOrder, builder).run(
                    framework().toString,
                    path.toAbsolutePath.toString,
                    Configuration.config().configPath().toAbsolutePath.toString,
                    cp,
                    outputPath.toAbsolutePath.toString).exitValue()

            if (exitCode == 0) {
                autoClose(Source.fromFile(outputPath.toAbsolutePath.toString).bufferedReader())(reader =>
                    Try(new Gson().fromJson(reader, classOf[TestRunResult])))
            } else {
                Failure(new Exception("Non-zero exit code: " ++ exitCode.toString))
            }
        }).flatMap(_.flatten.toOption)).flatten

    def framework(): TestFramework
    def project(): MavenProject

    def execution(testOrder: Stream[String], executionInfoBuilder: ExecutionInfoBuilder): ExecutionInfo
}

trait RunnerProvider[A <: Runner] {
    def withFramework(project: MavenProject, framework: TestFramework): A
}

object RunnerProvider {
    def from(project: MavenProject): Option[Runner] =
        TestFramework.testFramework(project).map(FixedOrderRunner.withFramework(project, _))
}
