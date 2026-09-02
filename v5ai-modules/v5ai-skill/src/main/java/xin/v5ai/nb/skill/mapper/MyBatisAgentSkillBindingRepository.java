//package xin.v5ai.nb.skill.mapper;
//
//import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
//import org.springframework.stereotype.Repository;
//import xin.v5ai.nb.skill.core.AgentSkillBindingService;
//import xin.v5ai.nb.skill.domain.AgentSkillBinding;
//import xin.v5ai.nb.skill.domain.vo.AgentSkillBindingDTO;
//
//import java.util.List;
//
///**
// * {@link AgentSkillBindingService} 的 MyBatis-Plus 实现（运行时按 Agent 读取 Skill 绑定）。
// *
// * @author ZYW
// * @since 2026-08-22
// */
//@Repository
//public class MyBatisAgentSkillBindingRepository implements AgentSkillBindingService {
//
//    private final AgentSkillBindingMapper bindingMapper;
//
//    public MyBatisAgentSkillBindingRepository(AgentSkillBindingMapper bindingMapper) {
//        this.bindingMapper = bindingMapper;
//    }
//
//    @Override
//    public List<AgentSkillBindingDTO> selectList(String agentKey) {
//        return bindingMapper.selectList(new LambdaQueryWrapper<AgentSkillBinding>()
//                        .eq(AgentSkillBinding::getAgentKey, agentKey))
//                .stream()
//                .map(this::toDomain)
//                .toList();
//    }
//
//    private AgentSkillBindingDTO toDomain(AgentSkillBinding entity) {
//        var dto = new AgentSkillBindingDTO();
//        dto.setAgentKey(entity.getAgentKey());
//        dto.setSkillId(entity.getSkillId());
//        dto.setCreatedAt(entity.getCreatedAt());
//        return dto;
//    }
//}
