package xin.v5ai.nb.infrastructure.workflow;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("v5ai_workflow")
public class WorkflowEntity {
    @TableId
    private Long id;
    private String workflowKey;
    private String name;
    private String description;
    private String status;
    private String draftDefinition;
    private String publishedDefinition;
    private Long publishedVersion;
    private Instant publishedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getWorkflowKey() { return workflowKey; }
    public void setWorkflowKey(String workflowKey) { this.workflowKey = workflowKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDraftDefinition() { return draftDefinition; }
    public void setDraftDefinition(String draftDefinition) { this.draftDefinition = draftDefinition; }
    public String getPublishedDefinition() { return publishedDefinition; }
    public void setPublishedDefinition(String publishedDefinition) { this.publishedDefinition = publishedDefinition; }
    public Long getPublishedVersion() { return publishedVersion; }
    public void setPublishedVersion(Long publishedVersion) { this.publishedVersion = publishedVersion; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
