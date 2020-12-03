package edu.illinois.cs.testrunner.runner

import edu.illinois.cs.testrunner.coreplugin.TestPlugin
import edu.illinois.cs.testrunner.util.ProjectWrapper

class SimpleRunnerPlugin extends TestPlugin {
    override def execute(project: ProjectWrapper): Unit = {
        val runnerTry = RunnerFactory.from(project)

        if (runnerTry.isDefined) {

        }
    }
}
