package edu.illinois.cs.testrunner.util

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId}
import scala.collection.mutable.HashSet
import scala.collection.mutable.ListBuffer

object Utility {
    def timestamp(): String =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss").format(Instant.now().atZone(ZoneId.systemDefault()))

    def getAllMethods(clz: Class[_]): Stream[Method] = {
        var curClz = clz;
        val methods = new ListBuffer[Method]()
        val nameSet = HashSet[String]()
        while (curClz != null) {
            for (m <- curClz.getDeclaredMethods) {
                if (!nameSet.contains(m.getName) && Modifier.isPublic(m.getModifiers())) {
                    // exclude override method
                    methods.append(m)
                    nameSet.add(m.getName)
                }
            }
            curClz = curClz.getSuperclass
        }
        methods.toStream
    }
}
