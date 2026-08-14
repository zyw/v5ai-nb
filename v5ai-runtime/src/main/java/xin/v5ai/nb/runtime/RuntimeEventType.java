package xin.v5ai.nb.runtime;

public enum RuntimeEventType {
    RUN_STARTED,
    MODEL_CALL,
    TEXT_DELTA,
    TOOL_CALL,
    TOOL_RESULT,
    RETRIEVAL,
    PERMISSION_REQUIRED,
    MESSAGE_COMPLETED,
    RUN_FAILED,
    RUN_COMPLETED
}
