package edu.illinois.cs.testrunner.mavenplugin;

import org.apache.maven.project.MavenProject;

public abstract class TestPlugin {
    public TestPlugin() {}

    public abstract void execute(final MavenProject project);
}
