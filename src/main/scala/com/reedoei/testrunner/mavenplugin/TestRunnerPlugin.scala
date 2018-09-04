package com.reedoei.testrunner.mavenplugin

import java.nio.charset.Charset
import java.nio.file.{Files, Paths}

import com.reedoei.testrunner.execution.TestExecutorProvider
import com.reedoei.testrunner.testobjects.TestLocator
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.{LifecyclePhase, Mojo, Parameter, ResolutionScope}
import org.apache.maven.project.MavenProject

import scala.collection.JavaConverters._

/**
  * @author Reed Oei
  */
@Mojo(name = "testplugin",
      defaultPhase = LifecyclePhase.TEST,
      requiresDependencyResolution = ResolutionScope.RUNTIME)
class TestRunnerPlugin extends AbstractMojo {
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private var project: MavenProject = _

    @Parameter(property = "testrunner.source", defaultValue = "default")
    private var source: String = "default"

    def tests(): Stream[String] = {
        val path = Paths.get(source)

        if (Files.exists(path) && Files.isRegularFile(path)) {
            Files.readAllLines(path, Charset.defaultCharset).asScala.toStream
        } else {
            TestLocator.tests(project)
        }
    }

    override def execute(): Unit =
        TestExecutorProvider.from(project)
            .map(executor => executor.run(tests()))
}
