package edu.illinois.cs.testrunner.coreplugin;

import edu.illinois.cs.testrunner.util.ProjectWrapper;

public abstract class TestPlugin {
    public TestPlugin() {}

    public abstract void execute(final ProjectWrapper project);
}
