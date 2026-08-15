package xin.v5ai.nb.infrastructure.skill;

import java.util.List;

public interface SkillVersionGateway {
    void insertVersion(SkillVersionEntity entity);

    void updateVersionStatus(Long versionId, String status, java.time.Instant publishedAt);

    SkillVersionEntity selectVersionById(Long id);

    List<SkillVersionEntity> selectVersionsBySkill(Long skillId);
}
