package edu.illinois.cs.testrunner.mavenplugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import edu.illinois.cs.testrunner.util.ProjectWrapper;

public class MavenProjectWrapper implements ProjectWrapper {
    private MavenProject project;
    private Log logger;

    public MavenProjectWrapper(MavenProject project, Log logger) {
        this.project = project;
        this.logger = logger;
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
        logger.error(t);
    }

    public MavenProjectWrapper getParent() {
        return new MavenProjectWrapper(project.getParent(), logger);
    }

    public File getBasedir() {
        return project.getBasedir();
    }

    public String getGroupId() {
        return project.getGroupId();
    }

    public String getArtifactId() {
        return project.getArtifactId();
    }

    public String getVersion() {
        return project.getVersion();
    }

    public String getBuildDirectory() {
        return project.getBuild().getDirectory();
    }

    public List<String> getBuildTestOutputDirectories() {
        List<String> result = new ArrayList<>();
        result.add(project.getBuild().getTestOutputDirectory());
        return result;
    }

    public boolean containJunit4() {
        Set<Artifact> artifacts = project.getArtifacts();
        for (Artifact artifact : artifacts)
            if (artifact.getArtifactId().equals("junit"))
                return true;
        return false;
    }

    public boolean containJunit5() {
        Set<Artifact> artifacts = project.getArtifacts();
        for (Artifact artifact : artifacts)
            if (artifact.getArtifactId().equals("junit-jupiter") || artifact.getArtifactId().equals("junit-jupiter-api"))
                return true;
        return false;
    }

    public List<String> getClasspathElements(){
        List<String> result = new ArrayList<String>();
        try {
            result.addAll(project.getCompileClasspathElements());
            result.addAll(project.getRuntimeClasspathElements());
            result.addAll(project.getTestClasspathElements());
            result.addAll(project.getSystemClasspathElements());
        } catch (DependencyResolutionRequiredException e) {
            e.printStackTrace();
        }
        return result;
    }

    private <T> Stream<T> emptyIfNull(final T t) {
        return t == null ? Stream.empty() : Stream.of(t);
    }

    public Map<String, String> surefireEnvironment() {
        List<Plugin> plugins = project.getBuildPlugins();
        return plugins.stream().filter(p -> p.getArtifactId().equals("maven-surefire-plugin"))
                .flatMap(p -> emptyIfNull(p.getConfiguration())).flatMap(conf -> {
                    if (conf instanceof Xpp3Dom) {
                        return Stream.of((Xpp3Dom) conf);
                    } else {
                        return Stream.empty();
                    }
                }).flatMap(conf -> emptyIfNull(conf.getChild("environmentVariables")))
                .flatMap(envVars -> emptyIfNull(envVars.getChildren())).flatMap(Arrays::stream)
                .collect(Collectors.toMap(Xpp3Dom::getName,
                        v -> v.getValue() == null ? "" : v.getValue()));
    }
}
