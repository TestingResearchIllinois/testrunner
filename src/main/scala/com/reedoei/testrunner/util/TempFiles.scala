package com.reedoei.testrunner.util

import java.io.File
import java.nio.file.{Files, Path, StandardOpenOption}

import scala.util.Try

object TempFiles {
    /**
      * Calls the function passed in with the path of a blank temporary file.
      * The file will no longer exist after this function ends
      */
    def withTempFile[A](f: Path => A): Option[A] = {
        val path = File.createTempFile("temp", null).toPath

        val result = Try(f(path))

        Files.deleteIfExists(path)

        result.toOption
    }

    def withSeq[S[_] <: Traversable[_], A, B](seq: S[A])(f: Path => B): Option[B] = {
        withTempFile(path => {
            // Don't need to clear file because withTempFile will always return a blank file
            for (s <- seq) {
                Files.write(path, (s.toString + System.lineSeparator()).getBytes(), StandardOpenOption.APPEND)
            }

            f(path)
        })
    }
}
