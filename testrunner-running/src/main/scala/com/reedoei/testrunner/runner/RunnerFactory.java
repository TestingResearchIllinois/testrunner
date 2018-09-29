package com.reedoei.testrunner.runner;

import com.reedoei.testrunner.data.framework.TestFramework;
import org.apache.maven.project.MavenProject;
import scala.Option;

public class RunnerFactory {
    public static Option<Runner> from(final MavenProject project) {
        return TestFramework.testFramework(project).map(framework -> SmartRunner.withFramework(project, framework));
    }
}
