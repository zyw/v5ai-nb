package xin.v5ai.nb.skill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.skill.domain.AgentSkillBinding;
import xin.v5ai.nb.skill.domain.vo.AgentSkillBindingVo;
import xin.v5ai.nb.skill.mapper.AgentSkillBindingMapper;
import xin.v5ai.nb.skill.service.IAgentSkillBindingService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentSkillBindingServiceImpl implements IAgentSkillBindingService {

    private final AgentSkillBindingMapper baseMapper;

    @Override
    public List<AgentSkillBindingVo> selectList(String agentKey) {
        return baseMapper.selectVoList(new LambdaQueryWrapper<AgentSkillBinding>()
                        .eq(AgentSkillBinding::getAgentKey, agentKey));
    }
}
