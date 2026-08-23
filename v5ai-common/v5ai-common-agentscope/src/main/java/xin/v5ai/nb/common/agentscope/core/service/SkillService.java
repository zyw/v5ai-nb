package xin.v5ai.nb.common.agentscope.core.service;

import xin.v5ai.nb.common.agentscope.core.domain.dto.SkillDTO;

public interface SkillService {
    SkillDTO selectById(Long id);
}
