package com.reedoei.testrunner.runner;

import com.reedoei.testrunner.configuration.Configuration;
import com.reedoei.testrunner.data.results.TestResult;
import com.reedoei.testrunner.data.results.TestRunResult;
import scala.Option;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestInfoStore {
    // This is the time to use when we don't know how long dts should take.
    // This time is still affected by the other modifiers below.
    private static final long MAX_DEFAULT_TIMEOUT = (long) Configuration.config().getProperty("runner.timeout.default", 3 * 3600);
    // How much longer we should wait than expected.
    private static final double TIMEOUT_MULTIPLIER = Configuration.config().getProperty("runner.timeout.multiplier", 4.0);
    // Adds a flat number of seconds to all timeouts
    private static final double TIMEOUT_OFFSET = Configuration.config().getProperty("runner.timeout.offset", 5.0);
    // Add a flat number of seconds per test.
    private static final double PER_TEST_MULTIPLIER = Configuration.config().getProperty("runner.timeout.pertest", 2.0);

    private final Map<String, TestInfo> testInfo = new HashMap<>();

    public TestInfoStore() {
    }

    public void update(final List<String> order, final Option<TestRunResult> results) throws FlakyTestException {
        results.foreach(result -> {
            update(order, result);
            return null;
        });
    }

    public void update(final List<String> order, final TestRunResult results) throws FlakyTestException {
        results.results().forEach((testName, testResult) -> {
            if (testInfo.containsKey(testName)) {
                testInfo.get(testName).updateWith(order, testResult);
            } else {
                testInfo.put(testName, new TestInfo(order, testName, testResult));
            }
        });
    }

    public long getTimeout(final List<String> order) {
        double totalExpectedTime = 0.0;

        for (final String s : order) {
            if (!testInfo.containsKey(s)) {
                totalExpectedTime = MAX_DEFAULT_TIMEOUT;
                break;
            } else {
                totalExpectedTime += testInfo.get(s).averageTime();
            }
        }

        return (long) (TIMEOUT_MULTIPLIER * (PER_TEST_MULTIPLIER * order.size() + TIMEOUT_OFFSET + totalExpectedTime));
    }

    public boolean isFlaky(final String testName) {
        return testInfo.containsKey(testName) && testInfo.get(testName).isFlaky();
    }
}
