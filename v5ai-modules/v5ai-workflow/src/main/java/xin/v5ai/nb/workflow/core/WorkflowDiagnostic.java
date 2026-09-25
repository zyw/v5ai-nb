package xin.v5ai.nb.workflow.core;

public record WorkflowDiagnostic(String severity, String code, String nodeId, String field, String message) {
}
