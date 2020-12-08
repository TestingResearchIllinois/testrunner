package edu.illinois.cs.testrunner.gradleplugin;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

import edu.illinois.cs.testrunner.coreplugin.TestPluginUtil;
import edu.illinois.cs.testrunner.configuration.Configuration;
import edu.illinois.cs.testrunner.util.ProjectWrapper;

public class TestPluginTask extends DefaultTask {
    @TaskAction
    public void testPluginTask() throws Exception {
        TestPluginUtil.setConfigs(null);
        TestPluginUtil.project = new GradleProjectWrapper(getProject());

        Class<?> clz = Class.forName(Configuration.config().getProperty(TestPluginUtil.pluginClassName,
                        TestPluginUtil.defaultPluginClassName));
        Object obj = clz.getConstructor().newInstance();
        clz.getMethod("execute", new Class[] {ProjectWrapper.class}).invoke(obj,
                TestPluginUtil.project);
    }
}
