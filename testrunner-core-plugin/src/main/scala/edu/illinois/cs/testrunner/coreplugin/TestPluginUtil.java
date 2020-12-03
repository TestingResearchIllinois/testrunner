package edu.illinois.cs.testrunner.coreplugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.logging.Logger;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

import edu.illinois.cs.testrunner.configuration.ConfigProps;
import edu.illinois.cs.testrunner.configuration.Configuration;

import org.apache.maven.project.MavenProject;

public class TestPluginUtil {
    final public static String pluginName = "testplugin";
    final public static String pluginClassName = "testplugin.className";
    final public static String defaultPluginClassName = "edu.illinois.cs.testrunner.coreplugin.TestRunner";
    private static List<URL> pluginCpURLs = null;
    private static String pluginCp = null;
    public static Logger logger;
    public static MavenProject project;

    public static void debug(String str) {
        logger.log(Level.FINE, str);
    }

    public static void info(String str) {
        logger.log(Level.INFO, str);
    }

    public static void error(String str) {
        logger.log(Level.SEVERE, str);
    }

    public static void error(Throwable t) {
        logger.log(Level.SEVERE, "", t);
    }

    private static void generate() {
        // TODO: When upgrading past Java 8, this will probably no longer work
        // (cannot cast any ClassLoader to URLClassLoader)
        URLClassLoader pluginClassloader = (URLClassLoader) Thread.currentThread().getContextClassLoader();
        pluginCpURLs = Arrays.asList(pluginClassloader.getURLs());
        pluginCp = String.join(File.pathSeparator,
            pluginCpURLs.stream().map((PluginCpUrl) -> PluginCpUrl.getPath()).toArray(String[]::new));
    }

    private static List<URL> pluginClasspathUrls() {
        if (pluginCpURLs == null) {
            generate();
        }

        return pluginCpURLs;
    }

    private static String pluginClasspath() {
        if (pluginCp == null) {
            generate();
        }

        return pluginCp;
    }

    private static void configJavaAgentPath() {
        pluginClasspathUrls().stream().filter((url) -> url.toString().contains("testrunner-maven-plugin"))
            .forEach((url) -> Configuration.config().setDefault("testplugin.javaagent", url.toString()));
    }

    private static void setDefaults(Configuration configuration) {
        configuration.setDefault(ConfigProps.CAPTURE_STATE, String.valueOf(false));
        configuration.setDefault(ConfigProps.UNIVERSAL_TIMEOUT, String.valueOf(-1));
        configuration.setDefault(ConfigProps.SMARTRUNNER_DEFAULT_TIMEOUT, String.valueOf(6 * 3600));
        configuration.setDefault("testplugin.runner.smart.timeout.multiplier", String.valueOf(4));
        configuration.setDefault("testplugin.runner.smart.timeout.offset", String.valueOf(5));
        configuration.setDefault("testplugin.runner.smart.timeout.pertest", String.valueOf(2));
        configuration.setDefault("testplugin.classpath", pluginClasspath());

        configJavaAgentPath();
    }

    public static void setConfigs(String propertiesPath) throws IOException {
        System.getProperties()
            .forEach((key, value) -> Configuration.config().properties().setProperty(key.toString(), value.toString()));

        if (propertiesPath != null && !propertiesPath.isEmpty()) {
            Configuration.reloadConfig(Paths.get(propertiesPath));
        }

        setDefaults(Configuration.config());
    }
}
