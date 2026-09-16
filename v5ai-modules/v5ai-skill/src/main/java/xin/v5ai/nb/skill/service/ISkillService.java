package xin.v5ai.nb.skill.service;

import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.core.domain.dto.OptionDTO;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.skill.domain.bo.SkillBo;
import xin.v5ai.nb.skill.domain.vo.SkillEditorVo;
import xin.v5ai.nb.skill.domain.vo.SkillUsageVo;
import xin.v5ai.nb.skill.domain.vo.SkillVersionVo;
import xin.v5ai.nb.skill.domain.vo.SkillVo;

import java.util.List;

/**
 * Skill 管理服务：上传解析、分页查询、版本发布、回滚、禁用与 AgentDTO 绑定。
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
     * 在线新建 Skill：创建 Skill（ACTIVE）+ DRAFT v1 + SKILL.md 骨架。
     *
     * @param name               技能名称（全局唯一，不能包含路径分隔符）
     * @param description        技能描述（skill.description + SKILL.md frontmatter）
     * @param versionDescription 版本描述（可选，DRAFT v1 的 version.description）
     * @return 新建的 Skill
     */
    SkillVo createSkillOnline(String name, String description, String versionDescription);

    /**
     * 打开在线编辑器：返回待编辑的 DRAFT 版本及全部文件。
     * 若无 DRAFT 版本，自动新建一个（从当前已发布版本拷贝文件作为编辑起点）。
     */
    SkillEditorVo getSkillEditor(Long skillId);

    /**
     * 在 DRAFT 版本中新建文件（重名报错）。
     */
    void createSkillFile(Long skillId, String filePath, String content);

    /**
     * 更新 DRAFT 版本中的文件内容（文件不存在报错）。
     */
    void updateSkillFile(Long skillId, String filePath, String content);

    /**
     * 从 DRAFT 版本删除文件（SKILL.md 禁止删除）。
     */
    void deleteSkillFile(Long skillId, String filePath);

    /**
     * AI 生成 SKILL.md 内容（不保存，返回全文）：保留技能名，需求说明作为 frontmatter description。
     */
    String generateSkillMd(Long skillId, Long modelId, String requirement);

    /**
     * AI 优化 DRAFT 版本中某文件的内容（不保存，返回全文）。
     */
    String optimizeSkillFile(Long skillId, Long modelId, String filePath, String requirement, String direction);

    /**
     * 查询单个 Skill。
     */
    SkillVo getSkill(Long id);

    /**
     * 禁用 Skill。
     */
    boolean disableSkill(Long id);

    /**
     * 启用 Skill。
     */
    boolean enableSkill(Long id);

    /**
     * 删除 Skill（级联删除其全部版本、文件与 AgentDTO 绑定）。
     * 存在已发布版本时禁止删除。
     */
    boolean deleteSkill(Long id);

    /**
     * 删除某 Skill 的一个版本（连同其文件）。
     * 已发布版本禁止删除。
     */
    boolean deleteSkillVersion(Long skillId, Long versionId);

    /**
     * 查询某 Skill 的版本列表（新版本在前）。
     */
    List<SkillVersionVo> listVersions(Long skillId);

    /**
     * 发布 DRAFT 版本：标记 PUBLISHED 并把 Skill 的当前版本指针指向它。
     */
    SkillVo publishVersion(Long skillId, Long versionId);

    /**
     * 下线已发布版本：状态回到 DRAFT（不可再注入）；
     * 若下线的是当前版本，同时清除 Skill 的当前版本指针（运行时停止注入）。
     */
    SkillVo offlineVersion(Long skillId, Long versionId);

    /**
     * 查询 Skill 当前被多少个 AgentDTO 绑定使用（用于下线前的使用提示）。
     */
    SkillUsageVo getSkillUsage(Long skillId);

    /**
     * 回滚：把 Skill 的当前版本指针指向某个已发布的旧版本。
     */
    SkillVo rollback(Long skillId, Long versionId);

    /**
     * 全量替换某 AgentDTO 绑定的 Skill 集合。
     */
    boolean bindSkills(String agentKey, List<Long> skillIds);

    /**
     * 查询某 AgentDTO 绑定的 Skill ID 集合。
     */
    List<Long> getSkillBindings(String agentKey);
}
