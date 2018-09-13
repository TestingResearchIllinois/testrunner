package com.reedoei.testrunner.mavenplugin

import java.io.File
import java.net.{URL, URLClassLoader}
import java.nio.file.Paths

import com.reedoei.testrunner.configuration.{ConfigProps, Configuration}
import org.apache.maven.execution.MavenSession
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations._
import org.apache.maven.project.{MavenProject, ProjectBuilder}

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

    def setDefaults(configuration: Configuration): Unit = {
        configuration.setDefault(ConfigProps.CAPTURE_STATE, false.toString)
        configuration.setDefault(ConfigProps.UNIVERSAL_TIMEOUT, (-1).toString)
        configuration.setDefault(ConfigProps.SMARTRUNNER_DEFAULT_TIMEOUT, (6*3600).toString)
        configuration.setDefault("testplugin.runner.smart.timeout.multiplier", 4.toString)
        configuration.setDefault("testplugin.runner.smart.timeout.offset", 5.toString)
        configuration.setDefault("testplugin.runner.smart.timeout.pertest", 2.toString)
    }

    def configJavaAgentPath(): Unit =
        TestPluginPlugin.pluginClasspathUrls()
            .find(_.toString.contains("testrunner-maven-plugin"))
            .foreach(url => Configuration.config().setDefault("testplugin.javaagent", url.toString))

    override def execute(): Unit = {
        val clz = Class.forName(className)

        if (propertiesPath != null && !propertiesPath.isEmpty) {
            setDefaults(Configuration.reloadConfig(Paths.get(propertiesPath)))
        }

        Configuration.config().setDefault("testplugin.classpath", TestPluginPlugin.pluginClasspath())
        configJavaAgentPath()

        val obj = clz.getConstructor().newInstance()
        clz.getMethod("execute", classOf[MavenProject]).invoke(obj, project)
    }
}

object TestPluginPlugin {
    private var pluginCpURLs: Array[URL] = null
    private var pluginCp: String = null

    def generate(): Unit = {
        // TODO: When upgrading past Java 8, this will probably no longer work
        // (cannot cast any ClassLoader to URLClassLoader)
        var pluginClassloader = Thread.currentThread().getContextClassLoader.asInstanceOf[URLClassLoader]
        pluginCpURLs = pluginClassloader.getURLs
        pluginCp = String.join(File.pathSeparator, pluginCpURLs.map(_.getPath).toList.asJava)
    }

    def pluginClasspathUrls(): Array[URL] = {
        if (pluginCpURLs == null) {
            generate()
        }

        pluginCpURLs
    }

    def pluginClasspath(): String = {
        if (pluginCp == null) {
            generate()
        }

        pluginCp
    }
}
