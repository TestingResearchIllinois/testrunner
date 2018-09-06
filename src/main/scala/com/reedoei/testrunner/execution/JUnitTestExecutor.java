package com.reedoei.testrunner.execution;

import com.reedoei.testrunner.configuration.Configuration;
import com.reedoei.testrunner.data.results.TestResult;
import com.reedoei.testrunner.data.results.TestResultFactory;
import com.reedoei.testrunner.data.results.TestRunResult;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;
import com.thoughtworks.xstream.security.AnyTypePermission;
import illinois.cs.dt.tools.diagnosis.DiffContainer;
import illinois.diaper.StateCapture;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runners.model.InitializationError;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class JUnitTestExecutor {
    public static TestRunResult runOrder(final ClassLoader loader,
                                         final List<String> testList,
                                         final boolean skipMissing,
                                         final boolean runSeparately)
            throws ClassNotFoundException{
        final JUnitTestExecutor executor;
        if (skipMissing) {
            executor = JUnitTestExecutor.skipMissing(loader, testList);
        } else {
            executor = JUnitTestExecutor.testOrder(loader, testList);
        }

        if (runSeparately) {
            return executor.executeSeparately();
        } else {
            return executor.executeWithJUnit4Runner();
        }
    }

	private final List<JUnitTest> testOrder = new ArrayList<>();
    private final Map<String, JUnitTest> testMap = new HashMap<>();
	private final Set<TestResult> knownResults = new HashSet<>();

    public JUnitTestExecutor(final JUnitTest test) {
        this(Collections.singletonList(test));
    }

    public JUnitTestExecutor(final List<JUnitTest> tests) {
        this(tests, new HashSet<>());
    }

    public JUnitTestExecutor(final List<JUnitTest> tests, final Set<TestResult> knownResults) {
        for (final JUnitTest test : tests) {
            if (test.isClassCompatible()) {
                testOrder.add(test);
                testMap.put(test.name(), test);
            } else {
                System.out.println("  Detected incompatible test case (" + test.name() + ")");
                knownResults.add(TestResultFactory.missing(test.name()));
            }
        }

        this.knownResults.addAll(knownResults);
    }

    //package.class.method
    public static JUnitTestExecutor singleton(final ClassLoader loader, final String fullMethodName) throws ClassNotFoundException {
        return JUnitTestExecutor.testOrder(loader, Collections.singletonList(fullMethodName));
    }

    public static JUnitTestExecutor skipMissing(final ClassLoader loader, final List<String> testOrder) {
        final List<JUnitTest> tests = new ArrayList<>();
        final Set<TestResult> knownResults = new HashSet<>();

        for (int i = 0; i < testOrder.size(); i++) {
            final String fullMethodName = testOrder.get(i);
            try {
                final JUnitTest test = new JUnitTest(loader, fullMethodName, i);

                tests.add(test);
            } catch (ClassNotFoundException e) {
                knownResults.add(TestResultFactory.missing(fullMethodName));
                System.out.println("  Skipped missing test : " + fullMethodName);
            } catch (ExceptionInInitializerError e) {
                knownResults.add(TestResultFactory.failOrError(e, 0.0, fullMethodName));
                System.out.println("Test failed in initialization: " + fullMethodName);
            } catch (Throwable e) {
                System.out.println("[ERROR] Encountered exception while initializing JUnitTest for '" + fullMethodName + "'");
                throw e;
            }
        }

        return new JUnitTestExecutor(tests, knownResults);
    }

    public static JUnitTestExecutor testOrder(final ClassLoader loader, final List<String> testOrder) throws ClassNotFoundException {
        final List<JUnitTest> tests = new ArrayList<>();

        for (int i = 0; i < testOrder.size(); i++) {
            final String fullMethodName = testOrder.get(i);

            try {
                tests.add(new JUnitTest(loader, fullMethodName, i));
            } catch (Throwable e) {
                System.out.println("[ERROR] Encountered exception while initializing JUnitTest for '" + fullMethodName + "'");
                throw e;
            }
        }

        return new JUnitTestExecutor(tests);
    }

    private boolean notContains(final Map<String, Double> testRuntimes,
                                final Map<String, JUnitTest> passingTests,
                                final String fullTestName) {
        if (!testRuntimes.containsKey(fullTestName)) {
            System.out.println("[ERROR]: No running time measured for test name '" + fullTestName + "'");
            return true;
        }

        if (!passingTests.containsKey(fullTestName)) {
            System.out.println("[ERROR]: No JUnitTest found for test name '" + fullTestName + "'");
            return true;
        }

        return false;
    }

    private Set<TestResult> results(final Result re, final List<JUnitTest> tests, final TestListener listener) {
        final Set<TestResult> results = new HashSet<>(knownResults);

        final Map<String, JUnitTest> passingTests = new HashMap<>();

        // To keep track of tests that have multiple errors, so we don't report the result multiple
        // times.
        final Set<String> handledTests = new HashSet<>();

        // So we can keep track of tests that didn't get run (i.e., skipped).
        final Set<String> allTests = new HashSet<>();
        for (final JUnitTest test : tests) {
            allTests.add(test.name());
        }

        // We can only mark a test as passing if it actually ran.
        for (final String testName : listener.runtimes().keySet()) {
            if (testMap.containsKey(testName)) {
                passingTests.put(testName, testMap.get(testName));
            } else {
                System.out.println("[ERROR] Unexpected test executed: " + testName);
            }
        }

        for (final Failure failure : re.getFailures()) {
            // If the description is a test (that is, a single test), then handle it normally.
            // Otherwise, the ENTIRE class failed during initialization or some such thing.
            if (failure.getDescription().isTest()) {
                final String fullTestName = JUnitTestRunner.fullName(failure.getDescription());

                if (handledTests.contains(fullTestName) || notContains(listener.runtimes(), passingTests, fullTestName)) {
                    continue;
                }

                results.add(TestResultFactory.failOrError(failure.getException(), listener.runtimes().get(fullTestName), fullTestName));

                passingTests.remove(fullTestName);
                allTests.remove(fullTestName);
                handledTests.add(fullTestName);
            } else {
                // The ENTIRE class failed, so we need to mark every test from this class as failing.
                final String className = failure.getDescription().getClassName();

                // Make a set first so that we can modify the original hash map
                for (final JUnitTest test : testOrder) {
                    if (test.javaClass().getCanonicalName().equals(className) && !handledTests.contains(test.name())) {
                        results.add(TestResultFactory.failOrError(failure.getException(), 0.0, test.name()));

                        if (passingTests.containsKey(test.name())) {
                            passingTests.remove(test.name());
                            allTests.remove(test.name());
                            handledTests.add(test.name());
                        }
                    }
                }
            }
        }

        for (final String fullMethodName : listener.ignored()) {
            if (!handledTests.contains(fullMethodName)) {
                results.add(TestResultFactory.ignored(fullMethodName));
                allTests.remove(fullMethodName);
                handledTests.add(fullMethodName);
            }
        }

        for (final String fullMethodName : passingTests.keySet()) {
            if (handledTests.contains(fullMethodName) || notContains(listener.runtimes(), passingTests, fullMethodName)) {
                continue;
            }

            results.add(TestResultFactory.passing(listener.runtimes().get(fullMethodName), fullMethodName));
            allTests.remove(fullMethodName);
        }

        for (final String fullMethodName : allTests) {
            if (!handledTests.contains(fullMethodName)) {
                results.add(TestResultFactory.missing(fullMethodName));
            }
        }

        return results;
    }

    /**
     * This is the method that calls XStream to serialize the state map into a string.
     *
     * @param  state  the string to object map representing the roots of the state
     * @return        string representing the serialized input state
     */
    private String serializeRoots(Map<String, DiffContainer> state) {
        XStream xstream = getXStreamInstance();
        String s = "";
        try {
            s = xstream.toXML(state);
            s = StateCapture.sanitizeXmlChars(s);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return s;
    }

    private XStream getXStreamInstance() {
        XStream xstream = new XStream(new DomDriver());
        XStream.setupDefaultSecurity(xstream);
        xstream.addPermission(AnyTypePermission.ANY);
        xstream.setMode(XStream.XPATH_ABSOLUTE_REFERENCES);
        // Set fields to be omitted during serialization
        xstream.omitField(java.lang.ref.SoftReference.class, "timestamp");
        xstream.omitField(java.lang.ref.SoftReference.class, "referent");
        xstream.omitField(java.lang.ref.Reference.class, "referent");

        /*
        String ignores[][] = new String[][] {
            {"com.squareup.wire.Wire", "messageAdapters"},
            {"com.squareup.wire.Wire", "builderAdapters"},
            {"com.squareup.wire.Wire", "enumAdapters"},
            {"org.apache.http.impl.conn.CPool", "COUNTER"},
            {"org.apache.http.impl.conn.ManagedHttpClientConnectionFactory", "COUNTER"},
            {"org.apache.http.localserver.LocalTestServer", "TEST_SERVER_ADDR"},
            {"org.apache.http.impl.auth.NTLMEngineImpl", "RND_GEN"}};
        */

        for (String ignore : StateCapture.ignores) {
            int lastDot = ignore.lastIndexOf(".");
            String clz = ignore.substring(0,lastDot);
            String fld = ignore.substring(lastDot+1);
            try {
                xstream.omitField(Class.forName(clz), fld);
            } catch (Exception ex) {
                //ex.printStackTrace();
                //Do not throw runtime exception, since some modules might indeed not
                //load all classes in the project.
                //throw new RuntimeException(ex);
            }
        }

        return xstream;
    }

    private TestRunResult execute(final JUnitTestRunner runner) {
        final PrintStream currOut = System.out;
        final PrintStream currErr = System.err;

//        System.setOut(EMPTY_STREAM);
//        System.setErr(EMPTY_STREAM);

        final JUnitCore core = new JUnitCore();

        final TestListener listener = new TestListener();
        core.addListener(listener);
        final Result re;
        re = core.run(runner);

        if (Configuration.config().getProperty("runner.capture_state", false)) {
            try {
                Files.write(Paths.get("state-diff.xml"), serializeRoots(runner.getStateDiffs()).getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

//        System.setOut(currOut);
//        System.setErr(currErr);

        final Set<TestResult> results = results(re, runner.tests(), listener);

        final TestRunResult finalResult = TestRunResult.empty();

        for (final TestResult result : results) {
            finalResult.results().put(result.name(), result);
        }

        return finalResult;
    }

    private TestRunResult execute(final List<JUnitTest> tests) {
        // This will happen only if no tests are selected by the filter.
        // In this case, we will throw an exception with a name that makes sense.
        if (tests.isEmpty()) {
            throw new EmptyTestListException(testOrder);
        }

        try {
            return execute(new JUnitTestRunner(tests));
        } catch (InitializationError initializationError) {
            initializationError.printStackTrace();
        }

        return TestRunResult.empty();
    }

    public Optional<TestRunResult> execute() {
        try {
            final JUnitTestRunner runner = new JUnitTestRunner(testOrder);
            final TestRunResult results = execute(runner);

            return Optional.of(new TestRunResult(results.results(), runner.getStateDiffs()));
        } catch (InitializationError initializationError) {
            initializationError.printStackTrace();
        }

        return Optional.empty();
    }

    public TestRunResult executeSeparately() {
        final TestRunResult results = TestRunResult.empty();

        for (final JUnitTest test : testOrder) {
            final TestRunResult testResult = execute(Collections.singletonList(test));

            results.results().putAll(testResult.results());
            results.diffs().putAll(testResult.diffs());
        }

        return results;
    }

    public TestRunResult executeWithJUnit4Runner() {
        return execute(testOrder);
	}

}
