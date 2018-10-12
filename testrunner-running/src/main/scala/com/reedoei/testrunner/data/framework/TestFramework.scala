package com.reedoei.testrunner.data.framework

import com.reedoei.testrunner.util.MavenClassLoader
import org.apache.maven.project.MavenProject

import scala.util.Try

sealed abstract class TestFramework
case object JUnit extends TestFramework

object TestFramework {

    def testFramework(project: MavenProject): Option[TestFramework] = {
        val tryLoader = canLoad(new MavenClassLoader(project).loader)(_)

        if (tryLoader(classOf[org.junit.Test])) {
            Option(JUnit)
        } else {
            Option.empty
        }
    }

    private def canLoad(loader: ClassLoader)(clz: Class[_]): Boolean = Try(loader.loadClass(clz.getCanonicalName)).isSuccess
}