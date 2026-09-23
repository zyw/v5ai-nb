package xin.v5ai.nb.workflow.core;

import java.util.List;

public record WorkflowDefinition(
        List<WorkflowNode> nodes,
        List<WorkflowEdge> edges
) {
}
