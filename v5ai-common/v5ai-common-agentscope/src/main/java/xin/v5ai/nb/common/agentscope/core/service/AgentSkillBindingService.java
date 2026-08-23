package xin.v5ai.nb.common.agentscope.core.service;

import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentSkillBindingDTO;

import java.util.List;

public interface AgentSkillBindingService {
    List<AgentSkillBindingDTO> selectList(String agentKey);
}
