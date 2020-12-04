package edu.illinois.cs.testrunner.util

import java.io.File
import java.net.URLClassLoader

import edu.illinois.cs.testrunner.util.ProjectWrapper

import scala.collection.JavaConverters._

class ProjectClassLoader {
    def classpath(): String = String.join(File.pathSeparator, classpathElements.asJava)

    private var classpathElements: List[String] = _

    private var classLoader: ClassLoader = _

    def this(project: ProjectWrapper) = {
        this()

        this.classpathElements = project.getClasspathElements.asScala.toList

        this.classLoader = new URLClassLoader(classpathElements.map(new File(_).toURI.toURL).toArray)
    }

    def loader: ClassLoader = classLoader
}

