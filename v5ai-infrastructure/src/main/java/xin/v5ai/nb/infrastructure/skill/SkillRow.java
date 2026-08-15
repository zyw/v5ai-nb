package xin.v5ai.nb.infrastructure.skill;

import java.time.Instant;

/**
 * Skill 列表行：v5ai_skill JOIN v5ai_skill_version 带出当前生效版本号。
 */
public class SkillRow {
    private Long id;
    private String name;
    private String description;
    private String status;
    private Long currentVersionId;
    private Long currentVersion;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getCurrentVersionId() { return currentVersionId; }
    public void setCurrentVersionId(Long currentVersionId) { this.currentVersionId = currentVersionId; }
    public Long getCurrentVersion() { return currentVersion; }
    public void setCurrentVersion(Long currentVersion) { this.currentVersion = currentVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
