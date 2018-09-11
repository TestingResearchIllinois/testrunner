package com.reedoei.testrunner.mavenplugin

import java.io.FileInputStream
import java.nio.file.Paths
import java.util.Properties

import com.reedoei.testrunner.configuration.Configuration
import com.reedoei.testrunner.util.{MavenClassLoader, autoClose}
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations._
import org.apache.maven.project.MavenProject

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

    @Parameter(property = "testplugin.classname", defaultValue = "TestRunner")
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
        clz.getMethod("execute", classOf[Properties], classOf[MavenProject])
           .invoke(obj, Configuration.config().properties(), project)
    }
}
