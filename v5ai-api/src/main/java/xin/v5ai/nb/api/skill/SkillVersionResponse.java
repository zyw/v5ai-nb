package xin.v5ai.nb.api.skill;

import xin.v5ai.nb.skill.SkillVersion;
import xin.v5ai.nb.skill.SkillVersionStatus;

import java.time.Instant;

public record SkillVersionResponse(
        Long id,
        Long skillId,
        Long version,
        SkillVersionStatus status,
        String description,
        Instant publishedAt,
        Instant createdAt
) {
    public static SkillVersionResponse from(SkillVersion version) {
        return new SkillVersionResponse(
                version.id(),
                version.skillId(),
                version.version(),
                version.status(),
                version.description(),
                version.publishedAt(),
                version.createdAt());
    }
}
