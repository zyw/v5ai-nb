package xin.v5ai.nb.api.workflow;

import xin.v5ai.nb.workflow.WorkflowRun;
import xin.v5ai.nb.workflow.WorkflowRunStatus;

import java.time.Instant;
import java.util.Map;

public record WorkflowRunResponse(
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
    public static WorkflowRunResponse from(WorkflowRun run) {
        return new WorkflowRunResponse(
                run.runId(),
                run.workflowKey(),
                run.workflowVersion(),
                run.status(),
                run.inputs(),
                run.outputs(),
                run.error(),
                run.startedAt(),
                run.finishedAt(),
                run.createdAt());
    }
}
