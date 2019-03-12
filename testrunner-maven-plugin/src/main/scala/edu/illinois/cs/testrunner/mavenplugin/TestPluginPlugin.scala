package edu.illinois.cs.testrunner.mavenplugin

import java.io.File
import java.net.{URL, URLClassLoader}
import java.nio.file.Paths

import edu.illinois.cs.testrunner.configuration.{ConfigProps, Configuration}
import org.apache.maven.execution.MavenSession
import org.apache.maven.plugin.{AbstractMojo, MavenPluginManager}
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

    @Component
    private var pluginManager: MavenPluginManager = _

    @Parameter(defaultValue = "edu.illinois.cs.testrunner.mavenplugin.TestRunner")
    private var className = "TestPluginPlugin"

    @Parameter(defaultValue = "")
    private var propertiesPath = ""

    def setDefaults(configuration: Configuration): Unit = {
        configuration.setDefault(ConfigProps.CAPTURE_STATE, false.toString)
        configuration.setDefault(ConfigProps.UNIVERSAL_TIMEOUT, (-1).toString)
        configuration.setDefault(ConfigProps.SMARTRUNNER_DEFAULT_TIMEOUT, (6*3600).toString)
        configuration.setDefault("testplugin.runner.smart.timeout.multiplier", 4.toString)
        configuration.setDefault("testplugin.runner.smart.timeout.offset", 5.toString)
        configuration.setDefault("testplugin.runner.smart.timeout.pertest", 2.toString)
        configuration.setDefault("testplugin.classpath", TestPluginPlugin.pluginClasspath())

        configJavaAgentPath()
    }

    def configJavaAgentPath(): Unit =
        TestPluginPlugin.pluginClasspathUrls()
            .find(_.toString.contains("testrunner-maven-plugin"))
            .foreach(url => Configuration.config().setDefault("testplugin.javaagent", url.toString))

    override def execute(): Unit = {
        System.getProperties.forEach((key, value) =>
            Configuration.config().properties().setProperty(key.toString, value.toString))

        val clz = Class.forName(Configuration.config().getProperty("testplugin.className", className))

        if (propertiesPath != null && !propertiesPath.isEmpty) {
            Configuration.reloadConfig(Paths.get(propertiesPath))
        }

        setDefaults(Configuration.config())

//        val surefire = project.getPlugin("org.apache.maven.plugins:maven-surefire-plugin")
//        println(surefire)
//        println(project.getRemotePluginRepositories)
//        println(project.getClassRealm)
//
//        println(getPluginContext)
//        println(getPluginContext.get("pluginDescriptor"))
//
//        val pluginDescriptor: PluginDescriptor = getPluginContext.get("pluginDescriptor").asInstanceOf[PluginDescriptor]
//
//        println(pluginDescriptor.getClassRealm)
//
//        val surefireDescriptor = pluginManager.getMojoDescriptor(surefire, "test",
//            project.getRemotePluginRepositories, session.getRepositorySession)
//        surefireDescriptor.getPluginDescriptor.setClassRealm(pluginDescriptor.getClassRealm)
//        println(surefireDescriptor)

//        val containerField = classOf[DefaultMavenPluginManager].getDeclaredField("container")
//        containerField.setAccessible(true)
//        pluginManager.asInstanceOf[DefaultMavenPluginManager]
//        println(containerField.get(pluginManager).asInstanceOf[DefaultPlexusContainer].getCo

//        val configuredSurefire = pluginManager.getConfiguredMojo(classOf[AbstractMojo], session,
//            new MojoExecution(surefireDescriptor))
//
//        println(configuredSurefire)

        TestPluginPlugin.mojo = this
        TestPluginPlugin.mavenProject = project

        val obj = clz.getConstructor().newInstance()
        clz.getMethod("execute", classOf[MavenProject]).invoke(obj, project)
    }
}

object TestPluginPlugin {
    var mojo: AbstractMojo = _
    var mavenProject: MavenProject = _

    private var pluginCpURLs: Array[URL] = null
    private var pluginCp: String = null

    def debug(str: String): Unit = mojo.getLog.debug(str)
    def info(str: String): Unit = mojo.getLog.info(str)
    def error(str: String): Unit = mojo.getLog.error(str)
    def error(t: Throwable): Unit = mojo.getLog.error(t)

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
