package com.reedoei.testrunner.execution

import java.io.File
import java.net.URLClassLoader
import java.nio.file.{Files, Path, Paths}
import java.util.stream.Collectors

import com.reedoei.testrunner.configuration.Configuration

import scala.util.Try

class Executor

object Executor {
    def main(args: Array[String]): Unit = {
        System.exit(args match {
            case Array(testFramework, testsFile, configPath, classpath, outputPath) =>
                run(testFramework, Paths.get(testsFile), Paths.get(configPath), classpath, Paths.get(outputPath))
            case _ => 1
        })
    }

    def run(testFramework: String, testsFile: Path, configPath: Path, classpath: String, outputPath: Path): Int = {
        Configuration.reloadConfig(configPath)

        val tests = Files.lines(testsFile).collect(Collectors.toList())

        val loader = new URLClassLoader(classpath.split(File.pathSeparator).map(new File(_).toURI.toURL))

        val result = Try(testFramework match {
            case "JUnit" =>
                JUnitTestExecutor.runOrder(loader, tests, true, false)
                    .writeTo(outputPath.toAbsolutePath.toString)
            case _ => throw new Exception("An error ocurred while running tests.")
        }).toOption

        if (result.isDefined) {0} else {1}
    }
}
