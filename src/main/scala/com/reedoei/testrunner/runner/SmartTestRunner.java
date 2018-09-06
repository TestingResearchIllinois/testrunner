package com.reedoei.testrunner.runner;
/*
import com.google.common.util.concurrent.SimpleTimeLimiter;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.reedoei.eunomia.io.capture.CaptureOutStream;
import com.reedoei.eunomia.io.capture.CapturedOutput;
import com.reedoei.eunomia.util.Util;
import edu.edu.illinois.cs.dt.tools.configuration.Configuration;
import edu.edu.illinois.cs.dt.tools.runner.data.TestResult;
import edu.washington.cs.dt.TestExecResults;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;*/

public class SmartTestRunner {
    /*
    private static final SmartTestRunner master = new SmartTestRunner();
    private final Path javaAgent;

    private boolean throwOnFlaky = Configuration.config().getProperty("runner.throw_on_flaky", true);

    public static SmartTestRunner master() {
        return master;
    }

    private final TestInfoStore infoStore = new TestInfoStore();
    private final String classpath;

    public SmartTestRunner() {
        this(System.getProperty("java.class.path"));
    }

    public SmartTestRunner(final String classpath) {
        this(classpath, Paths.get(""));
    }

    public SmartTestRunner(final String classpath, final Path javaAgent) {
        this(classpath, javaAgent, true);
    }

    public SmartTestRunner(final String classpath, final boolean throwOnFlaky) {
        this(classpath, Paths.get(""), throwOnFlaky);
    }

    public SmartTestRunner(final String classpath, final Path javaAgent, final boolean throwOnFlaky) {
        this.classpath = classpath;
        this.javaAgent = javaAgent;
        this.throwOnFlaky = throwOnFlaky;
    }

    private boolean correctTestsRan(final List<String> order, final TestExecResults result) {
        return new HashSet<>(order).equals(result.getExecutionRecords().get(0).getNameToResultsMap().keySet());
    }

    @SafeVarargs
    public final TestResult runOrder(final List<String>... orders) throws Exception {
        return runOrder(Arrays.stream(orders).reduce(new ArrayList<>(), Util::prependAll));
    }

    public TestResult runOrder(final List<String> order) throws Exception {
        final TimeLimiter limiter = SimpleTimeLimiter.create(Executors.newSingleThreadExecutor());

        final long timeout = infoStore.getTimeout(order);
//        final String endTime = LocalDateTime.now().plusSeconds(timeout).toString();
//        System.out.printf(" Running %d dts until %s", order.size(), endTime);

        try {
            return limiter.callWithTimeout(runner(classpath, order), timeout, TimeUnit.SECONDS);
        } catch (UncheckedExecutionException e) {
            // Throw it as a FlakyTestException so it's easier to deal with.
            if (e.getCause() instanceof FlakyTestException) {
                throw (FlakyTestException) e.getCause();
            } else {
                throw e;
            }
        }
    }

    private Callable<TestResult> runner(String classpath, List<String> order) {
        return () -> {
            final CapturedOutput<TestExecResults> capture =
                    new CaptureOutStream<>(() -> new FixedOrderRunner(classpath, order, javaAgent.toString()).run())
                            .run();

            final Optional<TestExecResults> results = capture.value();

            if (results.isPresent() && correctTestsRan(order, results.get())) {
                return handleResult(order, results.get());
            } else {
                return handleError(order, capture);
            }
        };
    }

    private TestResult handleResult(final List<String> order, final TestExecResults results) {
        try {
            synchronized (infoStore) {
                infoStore.update(order, results);
            }
        } catch (FlakyTestException e) {
            if (throwOnFlaky) {
                throw e;
            }
        }

        return new TestResult(results);
    }

    private TestResult handleError(final List<String> order, final CapturedOutput<TestExecResults> capture) {
        final Path errorPath = Paths.get(order.get(order.size() - 1) + "-error-log.txt");

        System.out.println("[ERROR] An exception occurred while running an order with " + order.size() + " tests.");
        System.out.println("[ERROR] The full order and output will be written to: " + errorPath);

        try {
            Files.write(errorPath, (order + "\n" + capture.stringOutput()).getBytes());
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("[ERROR]: Could not write file. Writing to stdout:");

            System.out.println(capture.stringOutput());
        }

        if (capture.error() instanceof RuntimeException) {
            throw (RuntimeException)capture.error();
        } else {
            throw new RuntimeException(capture.error());
        }
    }

    public double averageTestTime() {
        return infoStore.averageTime();
    }

    public boolean isFlaky(final String testName) {
        return infoStore.isFlaky(testName);
    }*/
}
