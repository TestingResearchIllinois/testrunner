package edu.illinois.cs.testrunner.testobjects

import java.lang.annotation.Annotation
import java.lang.reflect.Modifier

import scala.util.Try

object GeneralTestClass {
    /**
      * Create a test class from given class name.
      *
      * We must pass in a classloader because we must load EXACTLY the same classes as used by the subject
      * which may differ from the versions used by this plugin/maven
      */
    def create(loader: ClassLoader, clzName: String): Option[GeneralTestClass] = {
        val testAnnotation: Class[_ <: Annotation] =
            loader.loadClass("org.junit.Test").asInstanceOf[Class[_ <: Annotation]]

        try {
            val clz = loader.loadClass(clzName)

            if (!Modifier.isAbstract(clz.getModifiers)) {
                val methods = clz.getMethods.toStream

                Try(if (methods.exists(_.getAnnotation(testAnnotation) != null)) {
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
}

trait GeneralTestClass {
    def tests(): Stream[String]
}
