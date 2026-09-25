package xin.v5ai.nb.workflow.core;

import xin.v5ai.nb.workflow.core.enums.WorkflowRunStatus;
import xin.v5ai.nb.workflow.core.enums.WorkflowRunSource;

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
        Instant createdAt,
        WorkflowRunSource source,
        Long draftRevision,
        String definitionSnapshot
) {
    public WorkflowRun(String runId, String workflowKey, Long workflowVersion, WorkflowRunStatus status,
                       Map<String, Object> inputs, Map<String, Object> outputs, String error,
                       Instant startedAt, Instant finishedAt, Instant createdAt) {
        this(runId, workflowKey, workflowVersion, status, inputs, outputs, error, startedAt, finishedAt,
                createdAt, WorkflowRunSource.PUBLISHED, null, null);
    }
}
