package xin.v5ai.nb.api.workflow;

public record CreateWorkflowRequest(
        String workflowKey,
        String name,
        String description
) {
}
