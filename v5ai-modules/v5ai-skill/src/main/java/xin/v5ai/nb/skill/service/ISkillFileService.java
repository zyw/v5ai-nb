package xin.v5ai.nb.skill.service;

import xin.v5ai.nb.skill.domain.vo.SkillFileVo;

import java.util.List;

public interface ISkillFileService {
    /**
     * 查询某 Skill 版本的原始文件列表。
     *
     * @param versionId Skill 版本 ID
     * @return 该版本下的文件（SKILL.md、prompts/、resources/ 等）
     */
    List<SkillFileVo> selectList(Long versionId);
}
