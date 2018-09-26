package com.reedoei.testrunner.mavenplugin

import java.nio.file.{Files, Paths}

import com.reedoei.testrunner.configuration.Configuration
import com.reedoei.testrunner.data.results.TestRunResult
import com.reedoei.testrunner.runner.RunnerFactory
import com.reedoei.testrunner.testobjects.TestLocator
import com.reedoei.testrunner.util.Utility
import org.apache.maven.project.MavenProject

import scala.collection.JavaConverters._

/**
  * @author Reed Oei
  */
class TestRunner extends TestPlugin {
    def tests(source: String, project: MavenProject): Stream[String] = {
        if (source == null) {
            TestLocator.tests(project)
        } else {
            val path = Paths.get(source)

            if (Files.exists(path) && Files.isRegularFile(path)) {
                Files.lines(path).iterator().asScala.toStream
            } else {
                TestLocator.tests(project)
            }
        }
    }

    def defaultOutputLocation(project: MavenProject): String =
        Paths.get(project.getBuild.getDirectory)
            .resolve("testrunner")
            .resolve(Utility.timestamp())
            .resolve("results.json")
            .toAbsolutePath
            .toString

    override def execute(project: MavenProject): Unit =
        RunnerFactory.from(project)
            .map(_.run(tests(Configuration.config().getProperty("testrunner.source", null), project)).get)
            .getOrElse(TestRunResult.empty())
            .writeTo(Configuration.config().getProperty("testrunner.output", defaultOutputLocation(project)))
}
