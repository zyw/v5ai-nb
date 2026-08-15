package xin.v5ai.nb.skill;

import java.util.List;

public interface ApplicationSkillBindingRepository {
    /** 全量替换某 Application 绑定的 Skill 集合。 */
    void save(String appKey, List<Long> skillIds);

    List<Long> findByAppKey(String appKey);

    void delete(String appKey);
}
