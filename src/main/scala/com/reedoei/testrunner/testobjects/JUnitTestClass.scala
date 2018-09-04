package com.reedoei.testrunner.testobjects

import org.junit.Test
import org.junit.runners.model.{FrameworkMethod, TestClass}

import scala.collection.JavaConverters._

class JUnitTestClass(clz: Class[_]) extends GeneralTestClass {
    private def junitTestClass: TestClass = new TestClass(clz)

    def fullyQualifiedName(fm: FrameworkMethod): String =
        fm.getDeclaringClass.getCanonicalName ++ fm.getName

    override def tests(): Stream[String] =
        junitTestClass.getAnnotatedMethods(classOf[Test]).asScala.toStream
            .map(fullyQualifiedName)
}
