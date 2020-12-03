package edu.illinois.cs.testrunner.runner;

import edu.illinois.cs.testrunner.data.framework.TestFramework;
import edu.illinois.cs.testrunner.util.MavenClassLoader;

import edu.illinois.cs.testrunner.util.ProjectWrapper;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import scala.Option;

public class RunnerFactory {
    public static Option<Runner> from(final ProjectWrapper project) {
        return TestFramework.testFramework(project)
                .map(framework -> create(framework, new MavenClassLoader(project).classpath(),
                        project.surefireEnvironment(), project.getBasedir().toPath()));
    }

    public static List<Runner> allFrom(final ProjectWrapper project) {
        return TestFramework.getListOfFrameworks(project).stream()
                .map(framework ->
                        create(framework, new MavenClassLoader(project).classpath(),
                               project.surefireEnvironment(), project.getBasedir().toPath()))
                .collect(Collectors.toList());
    }

    public static Runner create(final TestFramework framework, final String classpath,
                                final Map<String, String> environment, final Path outputPath) {
        return SmartRunner.withFramework(framework, classpath, environment, outputPath);
    }
}
