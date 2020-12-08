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

        try {
            val clz = Class.forName(Configuration.config().getProperty(TestPluginUtil.pluginClassName, className))
            TestPluginUtil.project = new MavenProjectWrapper(project, this.getLog)
            clz.getMethod("execute", classOf[ProjectWrapper]).invoke(clz.getConstructor().newInstance(), TestPluginUtil.project)
        } catch {
            case e: NoSuchMethodException => {
                val clz = Class.forName(Configuration.config().getProperty(TestPluginUtil.pluginClassName, className))
                // Needed for backward compatibility
                TestPluginPlugin.mojo = this
                TestPluginPlugin.mavenProject = project

                clz.getMethod("execute", classOf[MavenProject]).invoke(clz.getConstructor().newInstance(), project)
            }
        }
    }
}

// Needed for backward compatibility
object TestPluginPlugin {
    var mojo: AbstractMojo = _
    var mavenProject: MavenProject = _

    def debug(str: String): Unit = mojo.getLog.debug(str)
    def info(str: String): Unit = mojo.getLog.info(str)
    def error(str: String): Unit = mojo.getLog.error(str)
    def error(t: Throwable): Unit = mojo.getLog.error(t)
}
