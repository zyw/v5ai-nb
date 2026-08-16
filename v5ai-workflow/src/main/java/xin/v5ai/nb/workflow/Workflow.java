package xin.v5ai.nb.workflow;

import java.time.Instant;

public record Workflow(
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
    public boolean isActive() {
        return status != WorkflowStatus.DISABLED;
    }

    public boolean isPublished() {
        return status == WorkflowStatus.PUBLISHED && publishedDefinition != null;
    }

    public Workflow withDraft(String name, String description, WorkflowDefinition draft) {
        return new Workflow(id, workflowKey, name, description, status, draft,
                publishedDefinition, publishedVersion, publishedAt, createdAt, updatedAt);
    }

    public Workflow withPublished(WorkflowDefinition published, Long version, Instant at) {
        return new Workflow(id, workflowKey, name, description, WorkflowStatus.PUBLISHED,
                draftDefinition, published, version, at, createdAt, updatedAt);
    }
}
