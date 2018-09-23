package com.reedoei.testrunner.util

import java.nio.file.Path
import java.util.Objects
import java.util.concurrent.TimeUnit

case class ExecutionInfo(classpath: String, javaAgent: Option[Path],
                         javaOpts: List[String],
                         properties: List[(String, String)],
                         clz: Class[_], inheritIO: Boolean,
                         timeout: Long, timeoutUnit: TimeUnit) {
    /**
      * This is an ugly workaround for Scala objects, whose class names end with $ despite the static main method
      * being in the companion class without the $ (
      * https://stackoverflow.com/questions/52208297/scala-proper-way-to-get-name-of-class-for-an-object?noredirect=1#comment91366065_52208297)
      */
    def className: String = {
        val name = clz.getCanonicalName

        if (name.endsWith("$")) {
            name.substring(0, name.length - 1)
        } else {
            name
        }
    }

    def args(args: String*): List[String] =
        List("java", "-cp", classpath) ++
        javaAgent.map(p => List("-javaagent:" ++ p.toAbsolutePath.toString)).getOrElse(List.empty) ++
        javaOpts ++
        properties.map(p => "-D" ++ p._1 ++ "=" ++ p._2) ++
        List(Objects.requireNonNull(className)) ++
        args.toList

    def processBuilder(argVals: String*): ProcessBuilder = {
        if (inheritIO) {
            new ProcessBuilder(args(argVals:_*): _*).inheritIO()
        } else {
            new ProcessBuilder(args(argVals:_*): _*)
        }
    }

    def run(argVals: String*): Process = {
        val process = processBuilder(argVals:_*).start()

        if (!inheritIO) {
            new StreamGobbler(process.getInputStream).start()
            new StreamGobbler(process.getErrorStream).start()
        }

        if (timeout > 0) {
            process.waitFor(timeout, timeoutUnit)
        } else {
            process.waitFor()
        }

        process
    }
}
