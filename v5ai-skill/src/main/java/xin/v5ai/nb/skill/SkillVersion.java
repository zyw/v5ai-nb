package xin.v5ai.nb.skill;

import java.time.Instant;

/**
 * Skill 版本：上传包生成 DRAFT，发布后不可变，回滚即把 Skill 的当前版本指针指向某已发布版本。
 *
 * @param id          主键
 * @param skillId     所属 Skill
 * @param version     版本号（每个 Skill 内自增）
 * @param status      DRAFT / PUBLISHED
 * @param description 版本描述（上传时提供）
 * @param publishedAt 发布时间（未发布为 null）
 * @param createdAt   创建时间
 */
public record SkillVersion(
        Long id,
        Long skillId,
        Long version,
        SkillVersionStatus status,
        String description,
        Instant publishedAt,
        Instant createdAt
) {
}
