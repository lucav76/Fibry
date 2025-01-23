package eu.lucaventuri.fibry.ai;

import java.util.Map;

public record NodeResult<S extends Enum>(Map<String, Object> values, S nextStateOverride) {
    NodeResult(Map<String, Object> values) {
        this(values, null);
    }

    NodeResult(S newNextStateOverride) {
        this(null, newNextStateOverride);
    }

    NodeResult() {
        this(null, null);
    }
}
