package com.reedoei.testrunner.mavenplugin

import java.util.Properties

import org.apache.maven.project.MavenProject

trait TestPlugin {
    def execute(properties: Properties, project: MavenProject): Unit
}
