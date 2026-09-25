package xin.v5ai.nb.workflow.core;

import java.util.List;
import java.util.Map;

public record WorkflowDefinition(
        List<WorkflowNode> nodes,
        List<WorkflowEdge> edges,
        Integer schemaVersion,
        List<Map<String, Object>> inputs,
        List<Map<String, Object>> outputs
) {
    public WorkflowDefinition(List<WorkflowNode> nodes, List<WorkflowEdge> edges) {
        this(nodes, edges, 1, List.of(), List.of());
    }

    public WorkflowDefinition {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
        schemaVersion = schemaVersion == null ? 1 : schemaVersion;
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
    }
}
