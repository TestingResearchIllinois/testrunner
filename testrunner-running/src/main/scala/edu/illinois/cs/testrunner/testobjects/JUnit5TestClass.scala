package edu.illinois.cs.testrunner.testobjects

import java.lang.annotation.Annotation
import java.lang.reflect.Method

import scala.collection.JavaConverters._

class JUnit5TestClass(loader: ClassLoader, clz: Class[_]) extends GeneralTestClass {

    def fullyQualifiedName(method: Method): String =
        clz.getCanonicalName ++ "#" ++ method.getName

    override def tests(): Stream[String] = {
        val junit5TestAnnotation: Class[_ <: Annotation] =
            loader.loadClass("org.junit.jupiter.api.Test").asInstanceOf[Class[_ <: Annotation]]
        clz.getDeclaredMethods().toStream
                .filter(_.getAnnotation(junit5TestAnnotation) != null)
                .map(fullyQualifiedName)
    }
}
