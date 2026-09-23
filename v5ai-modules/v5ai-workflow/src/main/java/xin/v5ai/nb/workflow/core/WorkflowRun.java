package xin.v5ai.nb.workflow.core;

import xin.v5ai.nb.workflow.core.enums.WorkflowRunStatus;

import java.time.Instant;
import java.util.Map;

public record WorkflowRun(
        String runId,
        String workflowKey,
        Long workflowVersion,
        WorkflowRunStatus status,
        Map<String, Object> inputs,
        Map<String, Object> outputs,
        String error,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt
) {
}
