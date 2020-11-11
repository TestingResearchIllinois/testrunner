package edu.illinois.cs.testrunner.testobjects

import java.nio.file.{Path, Paths}

import edu.illinois.cs.testrunner.data.framework.TestFramework
import edu.illinois.cs.testrunner.util.MavenClassLoader
import org.apache.maven.plugin.surefire.util.DirectoryScanner
import org.apache.maven.project.MavenProject
import org.apache.maven.surefire.testset.TestListResolver

import scala.collection.JavaConverters._

object TestLocator {
    def testOutputPath(project: MavenProject): Path = Paths.get(project.getBuild.getTestOutputDirectory)

    def testClasses(project: MavenProject): Stream[String] =
        new DirectoryScanner(testOutputPath(project).toFile, TestListResolver.getWildcard)
        .scan().getClasses.asScala.toStream

    def tests(project: MavenProject, framework: TestFramework): Stream[String] =
        testClasses(project).flatMap(className =>
            GeneralTestClass
                .create(new MavenClassLoader(project).loader, className, framework)
                .map(_.tests()).getOrElse(Stream.empty))
}
