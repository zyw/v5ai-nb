package xin.v5ai.nb.api.workflow;

import xin.v5ai.nb.workflow.Workflow;
import xin.v5ai.nb.workflow.WorkflowDefinition;
import xin.v5ai.nb.workflow.WorkflowStatus;

import java.time.Instant;

public record WorkflowResponse(
        Long id,
        String workflowKey,
        String name,
        String description,
        WorkflowStatus status,
        WorkflowDefinition draftDefinition,
        WorkflowDefinition publishedDefinition,
        Long publishedVersion,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static WorkflowResponse from(Workflow workflow) {
        return new WorkflowResponse(
                workflow.id(),
                workflow.workflowKey(),
                workflow.name(),
                workflow.description(),
                workflow.status(),
                workflow.draftDefinition(),
                workflow.publishedDefinition(),
                workflow.publishedVersion(),
                workflow.publishedAt(),
                workflow.createdAt(),
                workflow.updatedAt());
    }
}
