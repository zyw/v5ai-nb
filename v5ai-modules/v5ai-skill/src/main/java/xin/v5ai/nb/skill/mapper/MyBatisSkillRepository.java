//package xin.v5ai.nb.skill.mapper;
//
//import org.springframework.stereotype.Repository;
//import xin.v5ai.nb.skill.core.SkillService;
//import xin.v5ai.nb.skill.domain.Skill;
//import xin.v5ai.nb.skill.domain.vo.SkillDTO;
//
///**
// * {@link SkillService} 的 MyBatis-Plus 实现（运行时按 ID 读取 Skill）。
// *
// * @author ZYW
// * @since 2026-08-22
// */
//@Repository
//public class MyBatisSkillRepository implements SkillService {
//
//    private final SkillMapper skillMapper;
//
//    public MyBatisSkillRepository(SkillMapper skillMapper) {
//        this.skillMapper = skillMapper;
//    }
//
//    @Override
//    public SkillDTO selectById(Long id) {
//        Skill entity = skillMapper.selectById(id);
//        return entity == null ? null : toDomain(entity);
//    }
//
//    private SkillDTO toDomain(Skill entity) {
//        var dto = new SkillDTO();
//        dto.setId(entity.getId());
//        dto.setName(entity.getName());
//        dto.setDescription(entity.getDescription());
//        dto.setStatus(entity.getStatus().name());
//        dto.setCurrentVersionId(entity.getCurrentVersionId());
//        return dto;
//    }
//}
