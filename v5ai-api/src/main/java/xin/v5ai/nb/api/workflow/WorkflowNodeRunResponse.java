package xin.v5ai.nb.api.workflow;

import xin.v5ai.nb.workflow.WorkflowNodeRun;
import xin.v5ai.nb.workflow.WorkflowNodeRunStatus;
import xin.v5ai.nb.workflow.WorkflowNodeType;

import java.time.Instant;
import java.util.Map;

public record WorkflowNodeRunResponse(
        Long id,
        String runId,
        String nodeId,
        WorkflowNodeType nodeType,
        WorkflowNodeRunStatus status,
        Map<String, Object> inputs,
        Map<String, Object> outputs,
        String error,
        Instant startedAt,
        Instant finishedAt
) {
    public static WorkflowNodeRunResponse from(WorkflowNodeRun run) {
        return new WorkflowNodeRunResponse(
                run.id(),
                run.runId(),
                run.nodeId(),
                run.nodeType(),
                run.status(),
                run.inputs(),
                run.outputs(),
                run.error(),
                run.startedAt(),
                run.finishedAt());
    }
}
