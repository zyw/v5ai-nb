package xin.v5ai.nb.infrastructure.skill;

import java.util.List;

public interface SkillGateway {
    void insertSkill(SkillEntity entity);

    void updateSkill(SkillEntity entity);

    SkillEntity selectSkillById(Long id);

    SkillEntity selectSkillByName(String name);

    List<SkillRow> selectAllWithCurrentVersion();

    /** 下拉选项投影：仅 id/name/status/current_version_id/current_version，不加载描述。 */
    List<SkillRow> selectSkillOptions();

    SkillRow selectWithCurrentVersion(Long id);

    void disableSkill(Long id);
}
