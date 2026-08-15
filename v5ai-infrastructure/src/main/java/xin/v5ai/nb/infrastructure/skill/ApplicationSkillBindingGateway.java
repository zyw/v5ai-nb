package xin.v5ai.nb.infrastructure.skill;

import java.util.List;

public interface ApplicationSkillBindingGateway {
    void insertBinding(ApplicationSkillBindingEntity entity);

    void deleteByAppKey(String appKey);

    List<ApplicationSkillBindingEntity> selectByAppKey(String appKey);
}
