package com.reedoei.testrunner.data.results;

import com.google.gson.Gson;
import com.reedoei.testrunner.data.results.TestResult;
import illinois.cs.dt.tools.diagnosis.DiffContainer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class TestRunResult {
    public static TestRunResult empty() {
        return new TestRunResult(new HashMap<>(), new HashMap<>());
    }

    private final Map<String, TestResult> results;
    private final Map<String, DiffContainer> diffs;

    public TestRunResult(final Map<String, TestResult> results, final Map<String, DiffContainer> diffs) {
        this.results = results;
        this.diffs = diffs;
    }

    public Map<String, TestResult> results() {
        return results;
    }

    public Map<String, DiffContainer> diffs() {
        return diffs;
    }

    private PrintStream outputStream(final String outputPath) throws IOException {
        if (outputPath == null || outputPath.equals("stdout")) {
            return System.out;
        } else {
            final File f = new File(outputPath);
            Files.createDirectories(f.toPath().getParent());
            return new PrintStream(new FileOutputStream(new File(outputPath)));
        }
    }

    public void writeTo(final String outputPath) {
        try (final PrintStream p = outputStream(outputPath)) {
            p.print(new Gson().toJson(this));
        } catch (IOException e) {
            e.printStackTrace(System.err);
        }
    }
}
