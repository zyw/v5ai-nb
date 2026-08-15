package xin.v5ai.nb.infrastructure.skill;

import org.springframework.stereotype.Repository;
import xin.v5ai.nb.skill.ApplicationSkillBindingRepository;

import java.util.List;

@Repository
public class MyBatisApplicationSkillBindingRepository implements ApplicationSkillBindingRepository {
    private final ApplicationSkillBindingGateway gateway;

    public MyBatisApplicationSkillBindingRepository(ApplicationSkillBindingGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public void save(String appKey, List<Long> skillIds) {
        gateway.deleteByAppKey(appKey);
        for (Long skillId : skillIds) {
            var entity = new ApplicationSkillBindingEntity();
            entity.setAppKey(appKey);
            entity.setSkillId(skillId);
            gateway.insertBinding(entity);
        }
    }

    @Override
    public List<Long> findByAppKey(String appKey) {
        return gateway.selectByAppKey(appKey).stream()
                .map(ApplicationSkillBindingEntity::getSkillId)
                .toList();
    }

    @Override
    public void delete(String appKey) {
        gateway.deleteByAppKey(appKey);
    }
}
