package xin.v5ai.nb.api.skill;

import xin.v5ai.nb.skill.Skill;
import xin.v5ai.nb.skill.SkillStatus;

import java.time.Instant;

public record SkillResponse(
        Long id,
        String name,
        String description,
        SkillStatus status,
        Long currentVersionId,
        Long currentVersion,
        Instant createdAt,
        Instant updatedAt
) {
    public static SkillResponse from(Skill skill) {
        return new SkillResponse(
                skill.id(),
                skill.name(),
                skill.description(),
                skill.status(),
                skill.currentVersionId(),
                skill.currentVersion(),
                skill.createdAt(),
                skill.updatedAt());
    }
}
