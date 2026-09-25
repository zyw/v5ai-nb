package xin.v5ai.nb.workflow.core;

import java.util.List;

public record WorkflowValidationResult(boolean valid, List<WorkflowDiagnostic> diagnostics) {
    public WorkflowValidationResult {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
