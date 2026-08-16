package xin.v5ai.nb.api.workflow;

import xin.v5ai.nb.workflow.WorkflowDefinition;

public record UpdateWorkflowRequest(
        String name,
        String description,
        WorkflowDefinition definition
) {
}
