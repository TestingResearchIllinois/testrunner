package edu.illinois.cs.testrunner.data.framework

import edu.illinois.cs.testrunner.testobjects.GeneralTestClass
import edu.illinois.cs.testrunner.testobjects.JUnitTestCaseClass
import edu.illinois.cs.testrunner.testobjects.JUnitTestClass
import edu.illinois.cs.testrunner.testobjects.JUnit5TestClass
import edu.illinois.cs.testrunner.util.Utility
import edu.illinois.cs.testrunner.util.ProjectWrapper
import org.apache.maven.project.MavenProject
import java.lang.annotation.Annotation
import java.lang.reflect.Modifier
import java.util.List;
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.util.Try
import java.util.logging.{Level, Logger}

object TestFramework {
    def testFramework(project: ProjectWrapper): Option[TestFramework] = {
        // This method returns either JUnit 4 or JUnit 5 framework.
        // If the project contains both JUnit 4 and JUnit 5 tests, return Option.empty
        // Please use getListOfFrameworks for project mixes JUnit 4 and 5 tests

        // Not sure why we have to cast here, but with this, Scala can't seem to figure out that
        // we should get a list of dependencies

        val containJUnit4Dependency = project.containJunit4
        val containJUnit5Dependency = project.containJunit5

        if (containJUnit4Dependency && containJUnit5Dependency) {
            Option.empty
        } else if (containJUnit4Dependency) {
            Option(JUnit)
        } else if (containJUnit5Dependency) {
            Option(JUnit5)
        } else {
            Option.empty
        }
    }

    def getListOfFrameworks(project: ProjectWrapper): List[TestFramework] = {
        val listBuffer = ListBuffer[TestFramework]()

        if (project.containJunit4) {
            listBuffer.append(JUnit)
        }

        if (project.containJunit5) {
            listBuffer.append(JUnit5)
        }

        listBuffer.toList.asJava
    }

    // Needed for backward compatibility
    def testFramework(project: MavenProject): Option[TestFramework] = {
        // This method returns either JUnit 4 or JUnit 5 framework.
        // If the project contains both JUnit 4 and JUnit 5 tests, return Option.empty
        // Please use getListOfFrameworks for project mixes JUnit 4 and 5 tests

        // Not sure why we have to cast here, but with this, Scala can't seem to figure out that
        // we should get a list of dependencies
        val artifacts = project.getArtifacts.asScala

        val containJUnit4Dependency = artifacts.exists(artifact => artifact.getArtifactId == "junit")
        val containJUnit5Dependency = artifacts.exists(artifact => artifact.getArtifactId == "junit-jupiter") ||
                                      artifacts.exists(artifact => artifact.getArtifactId == "junit-jupiter-api")

        if (containJUnit4Dependency && containJUnit5Dependency) {
            Option.empty
        } else if (containJUnit4Dependency) {
            Option(JUnit)
        } else if (containJUnit5Dependency) {
            Option(JUnit5)
        } else {
            Option.empty
        }
    }

    def getListOfFrameworks(project: MavenProject): List[TestFramework] = {
        val artifacts = project.getArtifacts.asScala
        val listBuffer = ListBuffer[TestFramework]()

        if (artifacts.exists(artifact => artifact.getArtifactId == "junit")) {
            listBuffer.append(JUnit)
        }

        if (artifacts.exists(artifact => artifact.getArtifactId == "junit-jupiter")
                || artifacts.exists(artifact => artifact.getArtifactId == "junit-jupiter-api")) {
            listBuffer.append(JUnit5)
        }

        listBuffer.toList.asJava
    }

    def junitTestFramework(): TestFramework = {
        JUnit
    }

}

trait TestFramework {
    // return corresponding subclass of GeneralTestClass if the class matches with the framework
    def tryGenerateTestClass(loader: ClassLoader, clzName: String): Option[GeneralTestClass]

    def getDelimiter(): String
}

object JUnit extends TestFramework {
	val methodAnnotationStr: String = "org.junit.Test"
	
        override def tryGenerateTestClass(loader: ClassLoader, clzName: String): Option[GeneralTestClass] = {

	val annotation: Class[_ <: Annotation] =
            loader.loadClass(methodAnnotationStr).asInstanceOf[Class[_ <: Annotation]]

        try {

            val clz = loader.loadClass(clzName)

            Logger.getGlobal().log(Level.INFO, s"Loaded class: $clzName")

            if (!Modifier.isAbstract(clz.getModifiers)) {

                val methods = clz.getMethods.toStream

                // Check if it is an integration test by name or directory

                val isIntegrationTestByName = clzName.endsWith("IT")
                val classFilePath = clz.getProtectionDomain.getCodeSource.getLocation.getPath
                val isIntegrationTestByDirectory = classFilePath.contains("/target/test-classes/")

                Logger.getGlobal().log(Level.INFO, s"Class $clzName integration test by name: $isIntegrationTestByName")
                Logger.getGlobal().log(Level.INFO, s"Class $clzName integration test by directory: $isIntegrationTestByDirectory")

                Try(if (methods.exists(_.getAnnotation(annotation) != null) || 
                        (isIntegrationTestByName && isIntegrationTestByDirectory)) {
                    Logger.getGlobal().log(Level.INFO, s"Class $clzName recognized as a test class.")
                    Option(new JUnitTestClass(loader, clz))
                } else if (loader.loadClass("junit.framework.TestCase").isAssignableFrom(clz)) {
                    Logger.getGlobal().log(Level.INFO, s"Class $clzName recognized as a TestCase class.")
                    Option(new JUnitTestCaseClass(loader, clz))
                } else {
                    Logger.getGlobal().log(Level.INFO, s"Class $clzName is not a test class, skipping.")
                    Option.empty
                }).toOption.flatten

            } else {
                Logger.getGlobal().log(Level.INFO, s"Class $clzName is abstract, skipping.")
                Option.empty

            }

        } catch {

            case e: NoClassDefFoundError =>
                Logger.getGlobal().log(Level.WARNING, s"Class $clzName could not be loaded due to NoClassDefFoundError.")
                Option.empty

        }

    }

    override def toString(): String = "JUnit"
    // the delimiter to split the class name and method name
    // for the fully qualified name of JUnit 4 test
    // like com.package.JUnit4TestClass.TestA
    override def getDelimiter(): String = "."

}

object JUnit5 extends TestFramework {

    val methodAnnotationStr: String = "org.junit.jupiter.api.Test"
    val nestedAnnotationStr: String = "org.junit.jupiter.api.Nested"
    val disabledAnnotationStr: String = "org.junit.jupiter.api.Disabled"


    override def tryGenerateTestClass(loader: ClassLoader, clzName: String): Option[GeneralTestClass] = {

    val testAnnotation: Class[_ <: Annotation] =
        loader.loadClass(methodAnnotationStr).asInstanceOf[Class[_ <: Annotation]]

    val disabledAnnotation: Class[_ <: Annotation] =
        loader.loadClass(disabledAnnotationStr).asInstanceOf[Class[_ <: Annotation]]


    try {

        val clz = loader.loadClass(clzName)
        Logger.getGlobal().log(Level.INFO, s"Loaded class: $clzName")

        if (clz.getAnnotation(disabledAnnotation) != null) {
            // Skip disabled class
            Logger.getGlobal().log(Level.INFO, s"Class $clzName is disabled, skipping.")
            return Option.empty

        }

        if (clz.isMemberClass) {
            val nestedAnnotation: Class[_ <: Annotation] =
                loader.loadClass(nestedAnnotationStr).asInstanceOf[Class[_ <: Annotation]]

            if (Modifier.isStatic(clz.getModifiers) ||
                clz.getAnnotation(nestedAnnotation) == null) {
                // Skip unqualified nested test class
                Logger.getGlobal().log(Level.INFO, s"Class $clzName is an unqualified nested test class, skipping.")
                return Option.empty
            }

        }

        // Check if it is an integration test by name or directory

        val isIntegrationTestByName = clzName.endsWith("IT")
        val classFilePath = clz.getProtectionDomain.getCodeSource.getLocation.getPath
        val isIntegrationTestByDirectory = classFilePath.contains("/target/test-classes/")

        Logger.getGlobal().log(Level.INFO, s"Class $clzName integration test by name: $isIntegrationTestByName")
        Logger.getGlobal().log(Level.INFO, s"Class $clzName integration test by directory: $isIntegrationTestByDirectory")


        if (!Modifier.isAbstract(clz.getModifiers) &&

            (clz.getDeclaredMethods.exists(_.getAnnotation(testAnnotation) != null) || (isIntegrationTestByName && isIntegrationTestByDirectory))) {
            Logger.getGlobal().log(Level.INFO, s"Class $clzName recognized as a test class.")
            Option(new JUnit5TestClass(loader, clz))

        } else {

            Logger.getGlobal().log(Level.INFO, s"Class $clzName is not a test class, skipping.")
            Option.empty

        }

    } catch {

        case e: NoClassDefFoundError =>
            Logger.getGlobal().log(Level.WARNING, s"Class $clzName could not be loaded due to NoClassDefFoundError.")
            Option.empty

    	    }

    	}
    override def toString(): String = "JUnit5"
    
    // the splitor to split the class name and method name
    // for the full qualified name of JUnit 5 test
    // like com.package.JUnit5TestClass#TestA
    override def getDelimiter(): String = "#"
}
