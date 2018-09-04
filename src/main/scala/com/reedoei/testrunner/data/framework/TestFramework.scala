package com.reedoei.testrunner.data.framework

import org.apache.maven.model.Dependency
import org.apache.maven.project.MavenProject

import scala.collection.JavaConverters._

sealed abstract class TestFramework
case object JUnit extends TestFramework

object TestFramework {
    def testFramework(project: MavenProject): Option[TestFramework] = {
        // Not sure why we have to cast here, but with this, Scala can't seem to figure out that
        // we should get a list of dependencies
        val deps = project.getDependencies.asScala.asInstanceOf[List[Dependency]]

        if (deps.exists(dep => dep.getArtifactId.eq("junit") && dep.getGroupId.eq("junit"))) {
            Option(JUnit)
        } else {
            Option.empty
        }
    }
}