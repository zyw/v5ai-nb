package xin.v5ai.nb.workflow.core;

import xin.v5ai.nb.workflow.core.enums.WorkflowNodeRunStatus;
import xin.v5ai.nb.workflow.core.enums.WorkflowNodeType;

import java.time.Instant;
import java.util.Map;

public record WorkflowNodeRun(
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
}
