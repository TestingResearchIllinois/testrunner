package com.reedoei.testrunner.runner

import java.io.File
import java.nio.file.Paths
import java.nio.file.Files
import java.util.UUID

import com.google.gson.Gson
import com.reedoei.testrunner.configuration.{ConfigProps, Configuration}
import com.reedoei.testrunner.data.framework.TestFramework
import com.reedoei.testrunner.data.results.TestRunResult
import com.reedoei.testrunner.execution.Executor
import com.reedoei.testrunner.util._
import org.apache.maven.project.MavenProject
import org.codehaus.plexus.util.xml.Xpp3Dom

import scala.collection.JavaConverters._
import scala.io.Source
import scala.util.{Failure, Try}

trait Runner {
    def runList(testOrder: java.util.List[String]): Try[TestRunResult] =
        run(testOrder.asScala.toStream)

    def runListWithCp(cp: String, testOrder: java.util.List[String]): Try[TestRunResult] =
        runWithCp(cp, testOrder.asScala.toStream)

    def run(testOrder: Stream[String]): Try[TestRunResult] = runWithCp(classpath(), testOrder)

    def makeBuilder(cp: String): ExecutionInfoBuilder = {
        val builder = new ExecutionInfoBuilder(classOf[Executor]).classpath(cp)

        if (Configuration.config().getProperty(ConfigProps.CAPTURE_STATE, false)) {
            builder.javaAgent(Paths.get(Configuration.config().getProperty("testplugin.javaagent")))
        }

        builder.environment(getSurefireEnvironment)
    }

    private def getSurefireEnvironment: java.util.Map[String, String] =
        project().getBuildPlugins.asScala
            .find(p => p.getArtifactId == "maven-surefire-plugin")
            .flatMap(p => Option(p.getConfiguration))
            .flatMap(conf => Option(conf.asInstanceOf[Xpp3Dom]))
            .flatMap(conf => Option(conf.getChild("environmentVariables")))
            .flatMap(envVars => Option(envVars.getChildren))
            .map(children => children
                .map(elem => elem.getName -> elem.getValue)
                .toMap
            )
            .getOrElse(Map.empty)
            .asJava

    def generateTestRunId(): String = System.currentTimeMillis() + "-" + UUID.randomUUID.toString

    def runWithCp(cp: String, testOrder: Stream[String]): Try[TestRunResult] =
        TempFiles.withSeq(testOrder)(path =>
        TempFiles.withTempFile(outputPath =>
        TempFiles.withProperties(Configuration.config().properties())(propertiesPath => {
            val builder = makeBuilder(cp + File.pathSeparator + Configuration.config().getProperty("testplugin.classpath"))

            val info = execution(testOrder, builder)

            val testRunId = generateTestRunId()

            val exitCode = info.run(
                    testRunId,
                    framework().toString,
                    path.toAbsolutePath.toString,
                    propertiesPath.toAbsolutePath.toString,
                    outputPath.toAbsolutePath.toString).exitValue()

            if (exitCode == 0) {
                autoClose(Source.fromFile(outputPath.toAbsolutePath.toString).bufferedReader())(reader =>
                    Try(new Gson().fromJson(reader, classOf[TestRunResult])))
            } else {
                // Try to copy the output log so that it can be inspected
                val failureLog = project().getBasedir.toPath.resolve("failing-test-output-" + testRunId)
                Files.deleteIfExists(failureLog)
                Files.copy(info.outputPath, failureLog)
                Failure(new Exception("Non-zero exit code (output in " + failureLog.toAbsolutePath + "): " ++ exitCode.toString))
            }
        }))).flatten.flatten.flatten.flatten

    def classpath(): String = new MavenClassLoader(project()).classpath()

    def framework(): TestFramework
    def project(): MavenProject

    def execution(testOrder: Stream[String], executionInfoBuilder: ExecutionInfoBuilder): ExecutionInfo
}

trait RunnerProvider[A <: Runner] {
    def withFramework(project: MavenProject, framework: TestFramework): A
}
