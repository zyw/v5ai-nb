package xin.v5ai.nb.workflow.core;

import xin.v5ai.nb.workflow.core.enums.WorkflowNodeType;

import java.util.Map;

public record WorkflowNode(
        String id,
        WorkflowNodeType type,
        String name,
        Map<String, Object> config
) {
}
