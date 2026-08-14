package xin.v5ai.nb.runtime;

import java.time.Instant;

public record RuntimeEvent(String runId, RuntimeEventType type, String payload, Instant createdAt) {
    public static RuntimeEvent started(String runId) {
        return new RuntimeEvent(runId, RuntimeEventType.RUN_STARTED, "", Instant.now());
    }

    public static RuntimeEvent modelCall(String runId, String modelName) {
        return new RuntimeEvent(runId, RuntimeEventType.MODEL_CALL, modelName, Instant.now());
    }

    public static RuntimeEvent textDelta(String runId, String payload) {
        return new RuntimeEvent(runId, RuntimeEventType.TEXT_DELTA, payload, Instant.now());
    }

    public static RuntimeEvent toolCall(String runId, String payload) {
        return new RuntimeEvent(runId, RuntimeEventType.TOOL_CALL, payload, Instant.now());
    }

    public static RuntimeEvent toolResult(String runId, String payload) {
        return new RuntimeEvent(runId, RuntimeEventType.TOOL_RESULT, payload, Instant.now());
    }

    public static RuntimeEvent retrieval(String runId, String payload) {
        return new RuntimeEvent(runId, RuntimeEventType.RETRIEVAL, payload, Instant.now());
    }

    public static RuntimeEvent permissionRequired(String runId, String payload) {
        return new RuntimeEvent(runId, RuntimeEventType.PERMISSION_REQUIRED, payload, Instant.now());
    }

    public static RuntimeEvent messageCompleted(String runId, String payload) {
        return new RuntimeEvent(runId, RuntimeEventType.MESSAGE_COMPLETED, payload, Instant.now());
    }

    public static RuntimeEvent failed(String runId, String message) {
        return new RuntimeEvent(runId, RuntimeEventType.RUN_FAILED, message, Instant.now());
    }

    public static RuntimeEvent completed(String runId) {
        return new RuntimeEvent(runId, RuntimeEventType.RUN_COMPLETED, "", Instant.now());
    }
}
