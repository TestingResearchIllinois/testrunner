package edu.illinois.cs.testrunner.gradleplugin;

import edu.illinois.cs.testrunner.coreplugin.TestPluginUtil;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;

public class TestPluginPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        Task task = project.getTasks().create(TestPluginUtil.pluginName, TestPluginTask.class);
        // Make testrunner depends on main classes (included in assemble task) and
        // test classes compilation and assemble task.
        for (Task buildTask : project.getTasksByName("assemble", true))
            task.dependsOn(buildTask);
        for (Task buildTask : project.getTasksByName("testClasses", true))
            task.dependsOn(buildTask);
    }

}
