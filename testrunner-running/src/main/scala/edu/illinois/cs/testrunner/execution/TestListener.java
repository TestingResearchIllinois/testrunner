package edu.illinois.cs.testrunner.execution;

import edu.illinois.cs.statecapture.StateCapture;
import edu.illinois.cs.statecapture.agent.MainAgent;
import org.apache.commons.io.FileUtils;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.io.File;
import java.io.IOException;
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

    private String readFile(String path) throws IOException {
        File file = new File(path);
        return FileUtils.readFileToString(file, "UTF-8");
    }

    @Override
    public void testStarted(Description description) throws Exception {
        String fullTestName = JUnitTestRunner.fullName(description);
        times.put(fullTestName, System.nanoTime());

        String phase = readFile(MainAgent.tmpfile);
        if(MainAgent.targetTestName.equals(fullTestName)) {
            if(phase.equals("3") || phase.equals("4")) {
                StateCapture sc = new StateCapture(fullTestName);
                System.out.println("MainAgent.targetTestName: " + MainAgent.targetTestName +
                        " fullTestName: " + fullTestName);
                System.out.println("phase: " + phase);
                System.out.println("test listener!!!!!!!!! Capturing the states!!!!!!!!!!!!!");
                sc.capture();
                //System.out.println("sc.dirty: " + sc.dirty);
            }
            /* else if(phase.equals("5")) {
                StateCapture sc = new StateCapture(fullTestName);
                System.out.println("MainAgent.targetTestName: " + MainAgent.targetTestName +
                        " fullTestName: " + fullTestName);
                System.out.println("phase: " + phase);
                System.out.println("test listener!!!!!!!!! diffing the fields in passorder!!!!!!!!!!!!!");
                sc.diffing();
            } */
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

        String phase = readFile(MainAgent.tmpfile);

        if(phase.startsWith("4")) {
            String polluter = phase.split(" ")[1];
            if(polluter.equals(fullTestName)) {
                StateCapture sc = new StateCapture(fullTestName);
                System.out.println("MainAgent.targetTestName: " + MainAgent.targetTestName +
                        " fullTestName: " + fullTestName);
                System.out.println("phase: " + phase);
                System.out.println("test listener at after!!!!!!!!! Capturing the states!!!!!!!!!!!!!");
                sc.capture();
            }
        }
        else if(phase.startsWith("diffFieldAfter ")) {
            String polluter = phase.split(" ")[1];
            if(polluter.equals(fullTestName)) {
                // reflect one field each time
                if(phase.split(" ").length == 3) {
                    System.out.println("test listener at after!!!!!!!!! reflection on the states after!!!!!!!!!!!!!");
                    StateCapture sc = new StateCapture(fullTestName);
                    String diffField = phase.split(" ")[2];
                    sc.fixing(diffField);
                }
                //reflect two fields
                else if(phase.split(" ").length >= 4){
                    StateCapture sc = new StateCapture(fullTestName);
                    String[] tmpLists = phase.split(" ");
                    List<String> fields = new ArrayList<>();
                    for(int i = 2 ; i < tmpLists.length ; i++) {
                        fields.add(tmpLists[i]);
                    }
                    sc.fixingFList(fields);
                }
            }
        }

        if(MainAgent.targetTestName.equals(fullTestName)) {
            if(phase.equals("2") || phase.equals("2tmp")) {
                System.out.println("MainAgent.targetTestName: " + MainAgent.targetTestName +
                        " fullTestName: " + fullTestName);
                System.out.println("phase: " + phase);

                StateCapture sc = new StateCapture(fullTestName);//CaptureFactory.StateCapture(fullTestName);
                System.out.println("test listener at after !!!!!!!!! Capturing the states!!!!!!!!!!!!!");
                sc.capture();
                //System.out.println("sc.dirty: " + sc.dirty);
            }
            /* else if(phase.equals("5doublevic")) {
                StateCapture sc = new StateCapture(fullTestName);
                System.out.println("MainAgent.targetTestName: " + MainAgent.targetTestName +
                        " fullTestName: " + fullTestName);
                System.out.println("phase: " + phase);
                System.out.println("test listener!!!!!!!!! diffing the fields in doublevictim order!!!!!!!!!!!!!");
                sc.diffing();
            } */
        }
    }
}
