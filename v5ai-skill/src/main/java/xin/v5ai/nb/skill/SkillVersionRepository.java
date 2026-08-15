package xin.v5ai.nb.skill;

import java.util.List;

public interface SkillVersionRepository {
    SkillVersion save(SkillVersion version);

    SkillVersion findById(Long id);

    List<SkillVersion> findBySkillId(Long skillId);

    /** 返回该 Skill 下一个版本号（当前最大版本号 + 1，无则 1）。 */
    long nextVersion(Long skillId);

    void updateStatus(Long versionId, SkillVersionStatus status, java.time.Instant publishedAt);
}
