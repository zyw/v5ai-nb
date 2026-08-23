package xin.v5ai.nb.skill.mapper;

import org.springframework.stereotype.Repository;
import xin.v5ai.nb.common.agentscope.core.domain.dto.SkillDTO;
import xin.v5ai.nb.common.agentscope.core.service.SkillService;
import xin.v5ai.nb.skill.domain.Skill;

/**
 * {@link SkillService} 的 MyBatis-Plus 实现（运行时按 ID 读取 Skill）。
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Repository
public class MyBatisSkillRepository implements SkillService {

    private final SkillMapper skillMapper;

    public MyBatisSkillRepository(SkillMapper skillMapper) {
        this.skillMapper = skillMapper;
    }

    @Override
    public SkillDTO selectById(Long id) {
        Skill entity = skillMapper.selectById(id);
        return entity == null ? null : toDomain(entity);
    }

    private SkillDTO toDomain(Skill entity) {
        var dto = new SkillDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setStatus(entity.getStatus());
        dto.setCurrentVersionId(entity.getCurrentVersionId());
        return dto;
    }
}
