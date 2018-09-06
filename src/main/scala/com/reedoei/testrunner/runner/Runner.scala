package com.reedoei.testrunner.runner

import java.io.File
import java.net.URLClassLoader

import com.google.gson.Gson
import com.reedoei.testrunner.configuration.Configuration
import com.reedoei.testrunner.data.framework.TestFramework
import com.reedoei.testrunner.data.results.TestRunResult
import com.reedoei.testrunner.execution.Executor
import com.reedoei.testrunner.util._
import org.apache.maven.project.MavenProject

import scala.io.Source
import scala.util.{Failure, Try}

import scala.collection.JavaConverters._

trait Runner {
    def run(testOrder: Stream[String]): Option[TestRunResult] =
        TempFiles.withSeq(testOrder)(path => TempFiles.withTempFile(outputPath => {
            val cp = new MavenClassLoader(project()).classpath()


            // TODO: When upgrading past Java 8, this will probably no longer work
            // (cannot cast any ClassLoader to URLClassLoader)
            var pluginClassloader = Thread.currentThread().getContextClassLoader.asInstanceOf[URLClassLoader]
            var pluginCp = String.join(File.pathSeparator, pluginClassloader.getURLs.map(_.toString).toList.asJava)

            val builder = new ExecutionInfoBuilder(Executor.getClass).classpath(pluginCp)

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
        TestFramework.testFramework(project).map(SmartRunner.withFramework(project, _))
}
