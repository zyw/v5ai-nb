package xin.v5ai.nb.runtime;

import java.time.Instant;

public record RuntimeEvent(String runId, RuntimeEventType type, String payload, Instant createdAt) {
    public static RuntimeEvent started(String runId) {
        return new RuntimeEvent(runId, RuntimeEventType.RUN_STARTED, "", Instant.now());
    }

    public static RuntimeEvent textDelta(String runId, String payload) {
        return new RuntimeEvent(runId, RuntimeEventType.TEXT_DELTA, payload, Instant.now());
    }

    public static RuntimeEvent completed(String runId) {
        return new RuntimeEvent(runId, RuntimeEventType.RUN_COMPLETED, "", Instant.now());
    }
}
