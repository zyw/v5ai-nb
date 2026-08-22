package xin.v5ai.nb.workflow;

public record WorkflowEdge(
        String id,
        String source,
        String target,
        String sourceHandle
) {
}
