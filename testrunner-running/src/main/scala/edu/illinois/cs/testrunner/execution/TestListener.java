package edu.illinois.cs.testrunner.execution;

import edu.illinois.cs.statecapture.StateCapture;
import edu.illinois.cs.testrunner.configuration.Configuration;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.util.List;
import java.util.ArrayList;
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
        String state = Configuration.config().getProperty("statecapture.state", "");
        if (Configuration.config().getProperty("replay.dtname").equals(fullTestName)) {
            if (phase.equals("capture_before")) {
                StateCapture sc = new StateCapture(fullTestName);
                System.out.println("MainAgent.targetTestName: " + Configuration.config().getProperty("replay.dtname") +
                        " fullTestName: " + fullTestName);
                System.out.println("phase: " + phase);
                System.out.println("order: " + state);
                System.out.println("test listener!!!!!!!!! Capturing the states!!!!!!!!!!!!!");
                sc.capture();
            }
            System.out.println("testStarted end!!");
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
        String order = Configuration.config().getProperty("statecapture.state", "");
        String polluter = Configuration.config().getProperty("statecapture.polluter", "");
        String fieldName = Configuration.config().getProperty("statecapture.fieldName", "");
        System.out.println("PHASE: " + phase);

        if (phase.equals("capture_after")) {
            if (polluter.equals(fullTestName)) {
                StateCapture sc = new StateCapture(fullTestName);
                System.out.println("MainAgent.targetTestName: " + Configuration.config().getProperty("replay.dtname") +
                        " fullTestName: " + fullTestName);
                System.out.println("phase: " + phase);
                System.out.println("order: " + order);
                System.out.println("polluter: " + polluter);
                System.out.println("test listener at after!!!!!!!!! Capturing the states!!!!!!!!!!!!!");
                sc.capture();
            }
            else if (Configuration.config().getProperty("replay.dtname").equals(fullTestName)) {
                System.out.println("MainAgent.targetTestName: " + Configuration.config().getProperty("replay.dtname") +
                        " fullTestName: " + fullTestName);
                System.out.println("phase: " + phase);
                System.out.println("state: " + order);

                StateCapture sc = new StateCapture(fullTestName);//CaptureFactory.StateCapture(fullTestName);
                System.out.println("test listener at after !!!!!!!!! Capturing the states!!!!!!!!!!!!!");
                sc.capture();
            }
        }
        else if (phase.equals("load")) {
            if (polluter.equals(fullTestName)) {
                // reflect one field each time
                if (fieldName.split(",").length == 1) {
                    System.out.println("test listener at after!!!!!!!!! reflection on the states after!!!!!!!!!!!!!");
                    StateCapture sc = new StateCapture(fullTestName);
                    String diffField = fieldName.split(",")[0];
                    sc.fixing(diffField);
                }
                //reflect two fields
                else if (fieldName.split(",").length >= 2) {
                    StateCapture sc = new StateCapture(fullTestName);
                    String[] tmpLists = fieldName.split(",");
                    List<String> fields = new ArrayList<>();
                    for(int i = 2 ; i < tmpLists.length ; i++) {
                        fields.add(tmpLists[i]);
                    }
                    sc.fixingFieldList(fields);
                }
            }
        }
    }
}
