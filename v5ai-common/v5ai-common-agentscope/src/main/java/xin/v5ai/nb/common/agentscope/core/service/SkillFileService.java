package xin.v5ai.nb.common.agentscope.core.service;

import xin.v5ai.nb.common.agentscope.core.domain.dto.SkillFileDTO;

import java.util.List;

/**
 * Skill 文件查询服务端口：按版本查询 Skill 包内的文件列表。
 * 由基础设施层实现（MyBatis），供运行时做 Workspace 注入。
 */
public interface SkillFileService {
    /**
     * 查询某 Skill 版本的原始文件列表。
     *
     * @param versionId Skill 版本 ID
     * @return 该版本下的文件（SKILL.md、prompts/、resources/ 等）
     */
    List<SkillFileDTO> selectList(Long versionId);
}
