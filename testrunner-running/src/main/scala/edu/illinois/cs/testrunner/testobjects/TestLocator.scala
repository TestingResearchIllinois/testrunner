package edu.illinois.cs.testrunner.testobjects

import java.nio.file.{Path, Paths}

import edu.illinois.cs.testrunner.data.framework.TestFramework
import edu.illinois.cs.testrunner.util.MavenClassLoader
import org.apache.maven.plugin.surefire.util.DirectoryScanner
import edu.illinois.cs.testrunner.util.ProjectWrapper
import org.apache.maven.surefire.testset.TestListResolver

import scala.collection.JavaConverters._

object TestLocator {
    def testOutputPaths(project: ProjectWrapper): Stream[Path] = 
            project.getBuildTestOutputDirectories.asScala.toStream.map(outDir => Paths.get(outDir))

    def testClasses(project: ProjectWrapper): Stream[String] =
        testOutputPaths(project).flatMap(outPath => 
            new DirectoryScanner(outPath.toFile, TestListResolver.getWildcard)
                .scan().getClasses.asScala.toStream)

    def tests(project: ProjectWrapper, framework: TestFramework): Stream[String] =
        testClasses(project).flatMap(className =>
            GeneralTestClass
                .create(new MavenClassLoader(project).loader, className, framework)
                .map(_.tests()).getOrElse(Stream.empty))
}
