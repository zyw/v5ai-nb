package xin.v5ai.nb.common.agentscope.core.service;

import xin.v5ai.nb.common.agentscope.core.domain.dto.SkillFileDTO;

import java.util.List;

public interface SkillFileService {
    List<SkillFileDTO> selectList(Long versionId);
}
