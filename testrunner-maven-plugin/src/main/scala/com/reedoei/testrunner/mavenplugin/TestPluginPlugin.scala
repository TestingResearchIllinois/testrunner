package com.reedoei.testrunner.mavenplugin

import java.nio.file.Paths
import java.util.Properties

import com.reedoei.testrunner.configuration.Configuration
import org.apache.maven.execution.MavenSession
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations._
import org.apache.maven.project.{DefaultProjectBuildingRequest, MavenProject, ProjectBuilder}

import scala.collection.JavaConverters._

/**
 * @author Reed Oei
 */
@Mojo(name = "testplugin",
      defaultPhase = LifecyclePhase.TEST,
      requiresDependencyResolution = ResolutionScope.TEST)
@Execute(phase = LifecyclePhase.TEST_COMPILE,
         goal = "test-compile")
class TestPluginPlugin extends AbstractMojo {
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private var project: MavenProject = _

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private var session: MavenSession = _

    @Component
    private var projectBuilder: ProjectBuilder = _

    @Parameter(property = "testplugin.classname", defaultValue = "com.reedoei.testrunner.mavenplugin.TestRunner")
    private var className = "TestPluginPlugin"

    @Parameter(property = "testplugin.properties", defaultValue = "")
    private var propertiesPath = ""

    // TODO: Pass maven properties set by users

    def setDefaults(configuration: Configuration): Unit = {
        configuration.properties().setProperty("testplugin.runner.capture_state", false.toString)
        configuration.properties().setProperty("testplugin.runner.timeout.universal", (-1).toString)
        configuration.properties().setProperty("testplugin.runner.smart.timeout.default", (6*3600).toString)
        configuration.properties().setProperty("testplugin.runner.smart.timeout.multiplier", 4.toString)
        configuration.properties().setProperty("testplugin.runner.smart.timeout.offset", 5.toString)
        configuration.properties().setProperty("testplugin.runner.smart.timeout.pertest", 2.toString)
    }

    override def execute(): Unit = {
        val clz = Class.forName(className)

        if (propertiesPath != null && !propertiesPath.isEmpty) {
            setDefaults(Configuration.reloadConfig(Paths.get(propertiesPath)))
        }

        val obj = clz.getConstructor().newInstance()
        clz.getMethod("execute", classOf[MavenProject]).invoke(obj, project)
    }
}
