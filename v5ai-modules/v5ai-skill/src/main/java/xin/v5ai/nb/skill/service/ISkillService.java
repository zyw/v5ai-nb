package xin.v5ai.nb.skill.service;

import xin.v5ai.nb.common.core.domain.OptionDTO;
import xin.v5ai.nb.common.core.page.PageQuery;
import xin.v5ai.nb.common.core.page.PageResult;
import xin.v5ai.nb.skill.domain.bo.SkillBo;
import xin.v5ai.nb.skill.domain.vo.SkillVersionVo;
import xin.v5ai.nb.skill.domain.vo.SkillVo;

import java.util.List;

/**
 * Skill 管理服务：上传解析、分页查询、版本发布、回滚、禁用与 Agent 绑定。
 *
 * @author ZYW
 * @since 2026-08-22
 */
public interface ISkillService {

    /**
     * 分页查询 Skill。
     */
    PageResult<SkillVo> queryPageList(SkillBo bo, PageQuery pageQuery);

    /**
     * 查询 Skill 列表。
     */
    List<SkillVo> queryList(SkillBo bo);

    /**
     * 可绑定的 Skill 下拉选项：仅返回启用且已有已发布版本的 Skill。
     */
    List<OptionDTO> queryOptionList();

    /**
     * 上传 Skill 包：解析校验后，创建/复用 Skill 并生成一个 DRAFT 版本。
     *
     * @param packageBytes zip 包字节
     * @param description  版本描述（可选）
     * @return 创建/复用的 Skill
     */
    SkillVo uploadSkill(byte[] packageBytes, String description);

    /**
     * 查询单个 Skill。
     */
    SkillVo getSkill(Long id);

    /**
     * 禁用 Skill。
     */
    void disableSkill(Long id);

    /**
     * 查询某 Skill 的版本列表（新版本在前）。
     */
    List<SkillVersionVo> listVersions(Long skillId);

    /**
     * 发布 DRAFT 版本：标记 PUBLISHED 并把 Skill 的当前版本指针指向它。
     */
    SkillVo publishVersion(Long skillId, Long versionId);

    /**
     * 回滚：把 Skill 的当前版本指针指向某个已发布的旧版本。
     */
    SkillVo rollback(Long skillId, Long versionId);

    /**
     * 全量替换某 Agent 绑定的 Skill 集合。
     */
    void bindSkills(String agentKey, List<Long> skillIds);

    /**
     * 查询某 Agent 绑定的 Skill ID 集合。
     */
    List<Long> getSkillBindings(String agentKey);
}
