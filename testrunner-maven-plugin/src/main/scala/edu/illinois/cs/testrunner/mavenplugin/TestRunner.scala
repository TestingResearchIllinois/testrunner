package edu.illinois.cs.testrunner.mavenplugin

import java.nio.file.{Files, Paths}

import edu.illinois.cs.testrunner.configuration.Configuration
import edu.illinois.cs.testrunner.data.framework.JUnit
import edu.illinois.cs.testrunner.data.framework.JUnit5
import edu.illinois.cs.testrunner.data.results.TestRunResult
import edu.illinois.cs.testrunner.runner.RunnerFactory
import edu.illinois.cs.testrunner.testobjects.TestLocator
import edu.illinois.cs.testrunner.util.Utility
import org.apache.maven.project.MavenProject

import scala.collection.JavaConverters._

/**
  * @author Reed Oei
  */
class TestRunner extends TestPlugin {
    def tests(source: String, project: MavenProject): Stream[String] = {
        if (source == null) {
            locateTests(project)
        } else {
            val path = Paths.get(source)

            if (Files.exists(path) && Files.isRegularFile(path)) {
                Files.lines(path).iterator().asScala.toStream
            } else {
                locateTests(project)
            }
        }
    }

    def locateTests(project: MavenProject): Stream[String] = {
        // try locate JUnit4 tests first
        val tests = TestLocator.tests(project, JUnit)
        if (tests.length > 0) {
            return tests
        }
        // locate JUnit5 tests
        return TestLocator.tests(project, JUnit5)
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
            .getOrElse(TestRunResult.empty("failed"))
            .writeTo(Configuration.config().getProperty("testrunner.output", defaultOutputLocation(project)))
}
