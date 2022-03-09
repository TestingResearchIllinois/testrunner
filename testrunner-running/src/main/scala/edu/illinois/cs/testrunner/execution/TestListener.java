package edu.illinois.cs.testrunner.execution;

import edu.illinois.cs.statecapture.StateCapture;
import edu.illinois.cs.testrunner.configuration.Configuration;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

public class TestListener extends RunListener {
    private final Map<String, Long> times;
    private final Map<String, Double> testRuntimes;
    private final Set<String> ignoredTests;

    public TestListener() {
        testRuntimes = new HashMap<>();
        times = new HashMap<>();
        ignoredTests = new HashSet<>();
    }

    public Set<String> ignored() {
        return ignoredTests;
    }

    public Map<String, Double> runtimes() {
        return testRuntimes;
    }

    @Override
    public void testIgnored(Description description) throws Exception {
        ignoredTests.add(JUnitTestRunner.fullName(description));
    }

    @Override
    public void testStarted(Description description) throws Exception {
        String fullTestName = JUnitTestRunner.fullName(description);
        times.put(fullTestName, System.nanoTime());

        String phase = Configuration.config().getProperty("statecapture.phase", "");
        if (Configuration.config().getProperty("replay.dtname").equals(fullTestName)) {
            if (phase.equals("capture_before")) {
                StateCapture sc = new StateCapture(fullTestName);
                sc.capture();
            }
        }
    }

    @Override
    public void testFailure(Failure failure) throws Exception {
        failure.getException().printStackTrace();
    }

    @Override
    public void testAssumptionFailure(Failure failure) {
        failure.getException().printStackTrace();
    }

    @Override
    public void testFinished(Description description) throws Exception {
        final String fullTestName = JUnitTestRunner.fullName(description);

        if (times.containsKey(fullTestName)) {
            final long startTime = times.get(fullTestName);
            testRuntimes.put(fullTestName, (System.nanoTime() - startTime) / 1E9);
        } else {
            System.out.println("Test finished but did not start: " + fullTestName);
        }

        String phase = Configuration.config().getProperty("statecapture.phase", "");

        System.out.println("PHASE: " + phase);

        if (phase.equals("capture_after")) {
            if (Configuration.config().getProperty("replay.dtname").equals(fullTestName)) {
                StateCapture sc = new StateCapture(fullTestName);//CaptureFactory.StateCapture(fullTestName);
                sc.capture();
            }
        }
        else if (phase.equals("load")) {
            if (Configuration.config().getProperty("replay.dtname").equals(fullTestName)) {
                // reflect one field each time
                String fieldName = Configuration.config().getProperty("statecapture.fieldName", "");
                StateCapture sc = new StateCapture(fullTestName);
                String diffField = fieldName.split(",")[0];
                sc.fixing(diffField);
            }
        }
    }
}
