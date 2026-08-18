package xin.v5ai.nb.skill;

import java.util.List;

public interface SkillRepository {
    Skill save(Skill skill);

    Skill update(Skill skill);

    Skill findById(Long id);

    Skill findByName(String name);

    List<Skill> list();

    /**
     * Skill 下拉选项轻量视图。默认基于全量列表推导，持久化实现可覆盖为投影查询以降低大表开销。
     */
    default List<SkillOption> listOptions() {
        return list().stream()
                .map(skill -> new SkillOption(skill.id(), skill.name(), skill.status(),
                        skill.currentVersionId(), skill.currentVersion()))
                .toList();
    }

    void disable(Long id);
}
