package edu.illinois.cs.testrunner.gradleplugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.testing.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.illinois.cs.testrunner.util.ProjectWrapper;

public class GradleProjectWrapper implements ProjectWrapper {
    private Project project;
    private static Logger logger = LoggerFactory.getLogger(GradleProjectWrapper.class);

    public GradleProjectWrapper(Project project) {
        this.project = project;
    }

    public void debug(String str) {
        logger.debug(str);
    }

    public void info(String str) {
        logger.info(str);
    }

    public void error(String str) {
        logger.error(str);
    }

    public void error(Throwable t) {
        logger.error("", t);
    }

    public GradleProjectWrapper getParent() {
        return new GradleProjectWrapper(project.getParent());
    }

    public File getBasedir() {
        return project.getProjectDir();
    }

    public String getGroupId() {
        return project.getGroup().toString();
    }

    public String getArtifactId() {
        // No such api in gradle
        return "";
    }

    public String getVersion() {
        return project.getVersion().toString();
    }

    public String getBuildDirectory() {
        return project.getBuildDir().getPath();
    }

    public List<String> getBuildTestOutputDirectories() {
        List<String> result = new ArrayList();
        // Support java, scala and kotlin written junit tests
        for (Task task : project.getTasksByName("compileTestJava", false))
            for (File outputDir : task.getOutputs().getFiles().getFiles())
                result.add(outputDir.getPath());

        for (Task task : project.getTasksByName("compileTestScala", false))
            for (File outputDir : task.getOutputs().getFiles().getFiles())
                result.add(outputDir.getPath());

        for (Task task : project.getTasksByName("compileTestKotlin", false))
            for (File outputDir : task.getOutputs().getFiles().getFiles())
                result.add(outputDir.getPath());

        return result;
    }

    public boolean containJunit4() {
        for (Configuration config : project.getConfigurations().getAsMap().values())
            if (config.getAllDependencies().stream()
                    .filter(dependency -> dependency.getName().equals("junit")).count() > 0)
                return true;

        return false;
    }

    public boolean containJunit5() {
        for (Configuration config : project.getConfigurations().getAsMap().values())
            if (config.getAllDependencies().stream()
                    .filter(dependency -> dependency.getName().equals("junit-jupiter")
                            || dependency.getName().equals("junit-jupiter-api"))
                    .count() > 0)
                return true;

        return false;
    }

    public List<String> getClasspathElements() {
        // Retrieve all the classpath elements required by "test" task
        return project.getTasksByName("test", false).stream().filter(task -> task instanceof Test)
                .map(task -> (Test) task).flatMap(task -> task.getClasspath().getFiles().stream())
                .map(file -> file.getPath()).distinct().collect(Collectors.toList());
    }

    public Map<String, String> surefireEnvironment() {
        // No surefire support in gradle
        return new HashMap<String, String>();
    }
}
