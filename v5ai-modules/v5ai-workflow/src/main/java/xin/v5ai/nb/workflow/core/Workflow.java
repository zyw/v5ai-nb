package xin.v5ai.nb.workflow.core;

import xin.v5ai.nb.workflow.core.enums.WorkflowStatus;

import java.time.Instant;
import java.time.OffsetDateTime;

public record Workflow(
        Long id,
        String workflowKey,
        String name,
        String description,
        WorkflowStatus status,
        WorkflowDefinition draftDefinition,
        WorkflowDefinition publishedDefinition,
        Long publishedVersion,
        OffsetDateTime publishedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
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

    public Workflow withPublished(WorkflowDefinition published, Long version, OffsetDateTime at) {
        return new Workflow(id, workflowKey, name, description, WorkflowStatus.PUBLISHED,
                draftDefinition, published, version, at, createdAt, updatedAt);
    }
}
