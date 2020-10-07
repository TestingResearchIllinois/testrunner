package edu.illinois.cs.testrunner.data.framework

import edu.illinois.cs.testrunner.testobjects.GeneralTestClass
import edu.illinois.cs.testrunner.testobjects.JUnitTestCaseClass
import edu.illinois.cs.testrunner.testobjects.JUnitTestClass
import edu.illinois.cs.testrunner.testobjects.JUnit5TestClass
import org.apache.maven.project.MavenProject
import java.lang.annotation.Annotation
import java.lang.reflect.Modifier
import java.util.List;
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.util.Try

object TestFramework {
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

    override def tryGenerateTestClass(loader: ClassLoader, clzName: String)
            : Option[GeneralTestClass] = {
        val annotation: Class[_ <: Annotation] =
            loader.loadClass(methodAnnotationStr).asInstanceOf[Class[_ <: Annotation]]

        try {
            val clz = loader.loadClass(clzName)

            if (!Modifier.isAbstract(clz.getModifiers)) {
                val methods = clz.getMethods.toStream

                Try(if (methods.exists(_.getAnnotation(annotation) != null)) {
                    Option(new JUnitTestClass(loader, clz))
                } else if (loader.loadClass("junit.framework.TestCase").isAssignableFrom(clz)) {
                    Option(new JUnitTestCaseClass(loader, clz))
                } else {
                    Option.empty
                }).toOption.flatten
            } else {
                Option.empty
            }
        } catch {
            case e: NoClassDefFoundError => Option.empty
        }
    }

    override def toString(): String = "JUnit"

    // the splitor to split the class name and method name
    // for the full qualified name of JUnit 4 test
    // like com.package.JUnit4TestClass.TestA
    override def getDelimiter(): String = "."
}

object JUnit5 extends TestFramework {
    val methodAnnotationStr: String = "org.junit.jupiter.api.Test"

    override def tryGenerateTestClass(loader: ClassLoader, clzName: String)
            : Option[GeneralTestClass] = {
        val annotation: Class[_ <: Annotation] =
            loader.loadClass(methodAnnotationStr).asInstanceOf[Class[_ <: Annotation]]

        try {
            val clz = loader.loadClass(clzName)

            if (!Modifier.isAbstract(clz.getModifiers)) {
                val methods = clz.getMethods.toStream

                Try(if (methods.exists(_.getAnnotation(annotation) != null)) {
                    Option(new JUnit5TestClass(loader, clz))
                } else {
                    Option.empty
                }).toOption.flatten
            } else {
                Option.empty
            }
        } catch {
            case e: NoClassDefFoundError => Option.empty
        }
    }

    override def toString(): String = "JUnit5"

    // the splitor to split the class name and method name
    // for the full qualified name of JUnit 5 test
    // like com.package.JUnit5TestClass#TestA
    override def getDelimiter(): String = "#"
}