package com.reedoei.testrunner.testobjects

import org.junit.Test

object GeneralTestClass {
    def create(clz: Class[_]): Option[GeneralTestClass] = {
        val methods = clz.getMethods.toStream

        if (methods.exists(m => m.getAnnotation(classOf[Test]) != null)) {
            Option(new JUnitTestClass(clz))
        } else {
            Option.empty
        }
    }
}

trait GeneralTestClass {
    def tests(): Stream[String]
}
