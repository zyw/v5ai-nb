package xin.v5ai.nb.skill;

import java.util.List;

public interface SkillRepository {
    Skill save(Skill skill);

    Skill update(Skill skill);

    Skill findById(Long id);

    Skill findByName(String name);

    List<Skill> list();

    void disable(Long id);
}
