package xin.v5ai.nb.workflow.domain.vo;

import xin.v5ai.nb.workflow.core.enums.WorkflowNodeRunStatus;
import xin.v5ai.nb.workflow.core.enums.WorkflowNodeType;

import java.time.Instant;
import java.util.Map;

/** 节点运行记录及其运行时节点名称。 */
public record WorkflowNodeRunDetailVo(
        Long id,
        String runId,
        String nodeId,
        String nodeName,
        WorkflowNodeType nodeType,
        WorkflowNodeRunStatus status,
        Map<String, Object> inputs,
        Map<String, Object> outputs,
        String error,
        Instant startedAt,
        Instant finishedAt
) {
}
