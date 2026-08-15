package xin.v5ai.nb.skill;

import java.time.Instant;

/**
 * Skill 领域模型（平台侧）。
 *
 * @param id                    主键
 * @param name                  技能名（来自 SKILL.md frontmatter，全局唯一）
 * @param description           技能描述
 * @param status                启用状态
 * @param currentVersionId      当前生效（注入运行时）的已发布版本 ID（可为 null）
 * @param currentVersion        当前生效版本号（冗余展示，可为 null）
 * @param createdAt             创建时间
 * @param updatedAt             更新时间
 */
public record Skill(
        Long id,
        String name,
        String description,
        SkillStatus status,
        Long currentVersionId,
        Long currentVersion,
        Instant createdAt,
        Instant updatedAt
) {
    public boolean isActive() {
        return status == SkillStatus.ACTIVE;
    }

    public Skill withCurrentVersion(Long versionId, Long version) {
        return new Skill(id, name, description, status, versionId, version, createdAt, updatedAt);
    }
}
