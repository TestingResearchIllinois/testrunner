package com.reedoei.testrunner.execution

import java.nio.file.{Files, Path, Paths}
import java.util.stream.Collectors

import com.reedoei.testrunner.configuration.Configuration

import scala.util.Try


// This needs to be here so that Scala can find the class so we can execute from the command line
class Executor

object Executor {
    def main(args: Array[String]): Unit = {
        System.exit(args match {
            case Array(testFramework, testsFile, configPath, outputPath) =>
                run(testFramework, Paths.get(testsFile), Paths.get(configPath), Paths.get(outputPath))
            case _ => 1
        })
    }

    def run(testFramework: String, testsFile: Path, configPath: Path, outputPath: Path): Int = {
        Configuration.reloadConfig(configPath)

        // Only do this step if an agent class has been specified
        Option(System.getProperty("testplugin.agent_class")).foreach(AgentLoader.loadDynamicAgent)

        val tests = Files.lines(testsFile).collect(Collectors.toList())

        val result = Try(testFramework match {
            case "JUnit" =>
                JUnitTestExecutor.runOrder(tests, true, false)
                    .writeTo(outputPath.toAbsolutePath.toString)
            case _ => throw new Exception("An error ocurred while running tests.")
        }).toOption

        if (result.isDefined) {0} else {1}
    }
}
