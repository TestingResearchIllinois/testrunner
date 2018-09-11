package edu.illinois.cs.dt.tools.diagnosis;

import edu.illinois.diaper.StateCapture;
import org.junit.runners.model.Statement;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class StateDiff {
    private final StateCapture sc;
    private final String testName;

    public StateDiff(final String testName) {
        this.testName = testName;

        sc = new StateCapture(testName);
    }

    public DiffContainer diff(final Statement statement) throws Throwable {
        final LinkedHashMap<String, Object> beforeCapture = new LinkedHashMap<>(sc.capture());
        statement.evaluate();
        final LinkedHashMap<String, Object> afterCapture = new LinkedHashMap<>(sc.capture());

        final Map<String, String> beforeSerialized = new HashMap<>();
        beforeCapture.forEach((k, obj) -> {
            beforeSerialized.put(k, sc.serialize(obj));
        });

        final Map<String, String> afterSerialized = new HashMap<>();
        afterCapture.forEach((k, obj) -> {
            afterSerialized.put(k, sc.serialize(obj));
        });

        return new DiffContainer(testName, beforeSerialized, afterSerialized);
    }
}
