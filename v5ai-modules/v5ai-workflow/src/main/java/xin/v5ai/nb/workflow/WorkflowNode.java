package xin.v5ai.nb.workflow;

import java.util.Map;

public record WorkflowNode(
        String id,
        WorkflowNodeType type,
        String name,
        Map<String, Object> config
) {
}
