package edu.illinois.cs.testrunner.util

import java.io.File
import java.net.URLClassLoader

import org.apache.maven.project.MavenProject

import scala.collection.JavaConverters._

class MavenClassLoader {
    def classpath(): String = String.join(File.pathSeparator, classpathElements.asJava)

    private var classpathElements: List[String] = _

    private var classLoader: ClassLoader = _

    def this(project: MavenProject) = {
        this()

        this.classpathElements =
            (project.getCompileClasspathElements.asScala ++
            project.getRuntimeClasspathElements.asScala ++
            project.getTestClasspathElements.asScala ++
            project.getSystemClasspathElements.asScala).toList

        this.classLoader = new URLClassLoader(classpathElements.map(new File(_).toURI.toURL).toArray)
    }

    def loader: ClassLoader = classLoader
}

