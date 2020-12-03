package edu.illinois.cs.testrunner.mavenplugin

import java.io.File
import java.net.{URL, URLClassLoader}
import java.nio.file.Paths
import java.util.logging.Logger;

import edu.illinois.cs.testrunner.util.ProjectWrapper;
import edu.illinois.cs.testrunner.configuration.{ConfigProps, Configuration}
import edu.illinois.cs.testrunner.coreplugin.TestPluginUtil
import org.apache.maven.execution.MavenSession
import org.apache.maven.plugin.{AbstractMojo, MavenPluginManager}
import org.apache.maven.plugins.annotations._
import org.apache.maven.project.{MavenProject, ProjectBuilder}

import scala.collection.JavaConverters._

/**
 * @author Reed Oei
 */
@Mojo(name = TestPluginUtil.pluginName,
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

    @Parameter(defaultValue = TestPluginUtil.defaultPluginClassName)
    private var className = "TestPluginPlugin"

    @Parameter(defaultValue = "")
    private var propertiesPath = ""

    override def execute(): Unit = {
        TestPluginUtil.setConfigs(propertiesPath)
        val clz = Class.forName(Configuration.config().getProperty(TestPluginUtil.pluginClassName, className))

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

        TestPluginUtil.logger = Logger.getLogger(className)
        TestPluginUtil.project = new MavenProjectWrapper(project)

        val obj = clz.getConstructor().newInstance()
        clz.getMethod("execute", classOf[ProjectWrapper]).invoke(obj, TestPluginUtil.project)
    }
}