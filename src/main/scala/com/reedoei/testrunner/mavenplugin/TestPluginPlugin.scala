package com.reedoei.testrunner.mavenplugin

import java.io.FileInputStream
import java.nio.file.Paths
import java.util.Properties

import com.reedoei.testrunner.configuration.Configuration
import com.reedoei.testrunner.util.autoClose
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations._
import org.apache.maven.project.MavenProject

/**
 * @author Reed Oei
 */
@Mojo(name = "testplugin",
      defaultPhase = LifecyclePhase.TEST,
      requiresDependencyResolution = ResolutionScope.TEST)
class TestPluginPlugin extends AbstractMojo {
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private var project: MavenProject = _

    @Parameter(property = "testplugin.classname", defaultValue = "com.reedoei.testrunner.mavenplugin.TestRunner")
    private var className = "com.reedoei.testrunner.mavenplugin.TestPluginPlugin"

    @Parameter(property = "testplugin.properties", defaultValue = "")
    private var propertiesPath = ""

    override def execute(): Unit = {
        val clz = Class.forName(className)

        val properties = new Properties()

        if (properties != null && !propertiesPath.isEmpty) {
            autoClose(new FileInputStream(propertiesPath))(stream => properties.load(stream))

            Configuration.reloadConfig(Paths.get(propertiesPath))
        }

        val obj = clz.getConstructor().newInstance()
        clz.getMethod("execute", classOf[Properties], classOf[MavenProject])
           .invoke(obj, properties, project)
    }
}
