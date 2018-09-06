package com.reedoei.testrunner.runner

import com.reedoei.testrunner.data.framework.TestFramework
import com.reedoei.testrunner.util.{ExecutionInfo, ExecutionInfoBuilder}
import org.apache.maven.project.MavenProject

class FixedOrderRunner(mavenProject: MavenProject, testFramework: TestFramework) extends Runner {
    override def execution(testOrder: Stream[String], executionInfoBuilder: ExecutionInfoBuilder): ExecutionInfo =
        executionInfoBuilder.build()

    override def framework(): TestFramework = testFramework

    override def project(): MavenProject = mavenProject
}

object FixedOrderRunner extends RunnerFactory[FixedOrderRunner] {
    override def withFramework(project: MavenProject, framework: TestFramework): FixedOrderRunner =
        new FixedOrderRunner(project, framework)
}
