package illinois.cs.dt.tools.diagnosis;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class DiffContainer {
    private final Map<String, Diff> diffs = new HashMap<>();
    private final String testName;

    public DiffContainer(final String testName, final Map<String, String> before, final Map<String, String> after) {
        this.testName = testName;

        after.forEach((k, v) -> {
            final String bv = before.get(k);
            if (bv == null || !v.equals(bv)) {
                diffs.put(k, new Diff(bv, v));
            }
        });
    }

    public String testName() {
        return testName;
    }

    public Optional<Diff> getDiff(final String fqFieldName) {
        return Optional.ofNullable(diffs.get(fqFieldName));
    }

    public Map<String, Diff> getDiffs() {
        return diffs;
    }

    public class Diff {
        private final Object before;
        private final Object after;

        private Diff(Object before, Object after) {
            this.before = before;
            this.after = after;
        }

        public Object getBefore() {
            return before;
        }

        public Object getAfter() {
            return after;
        }
    }
}
