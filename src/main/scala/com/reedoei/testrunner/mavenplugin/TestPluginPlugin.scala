package com.reedoei.testrunner.mavenplugin

import com.reedoei.testrunner.testobjects.TestLocator
import org.apache.maven.execution.MavenSession
import org.apache.maven.plugin.{AbstractMojo, MojoExecutionException}
import org.apache.maven.plugins.annotations._
import org.apache.maven.project.{DefaultProjectBuildingRequest, MavenProject, ProjectBuilder, ProjectBuildingException}

/**
 * @author Reed Oei
 */
@Mojo(name = "testplugin",
      defaultPhase = LifecyclePhase.TEST,
      requiresDependencyResolution = ResolutionScope.RUNTIME)
class TestPluginPlugin extends AbstractMojo {
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private var project: MavenProject = _

    override def execute(): Unit = {
        println(TestLocator.tests(project).toList)
    }
}
