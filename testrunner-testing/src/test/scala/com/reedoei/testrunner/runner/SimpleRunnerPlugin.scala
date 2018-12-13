package com.reedoei.testrunner.runner

import com.reedoei.testrunner.mavenplugin.TestPlugin
import org.apache.maven.project.MavenProject

class SimpleRunnerPlugin extends TestPlugin {
    override def execute(project: MavenProject): Unit = {
        val runnerTry = RunnerFactory.from(project)

        if (runnerTry.isDefined) {

        }
    }
}
