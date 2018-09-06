package com.reedoei.testrunner.mavenplugin

import java.nio.file.{Files, Paths}
import java.util.Properties

import com.reedoei.testrunner.data.results.TestRunResult
import com.reedoei.testrunner.runner.RunnerProvider
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

    override def execute(properties: Properties, project: MavenProject): Unit =
        RunnerProvider.from(project)
            .flatMap(_.run(tests(properties.getProperty("testrunner.source"), project)))
            .getOrElse(TestRunResult.empty())
            .writeTo(properties.getProperty("testrunner.output", defaultOutputLocation(project)))
}
