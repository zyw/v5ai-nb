package xin.v5ai.nb.skill.domain.vo;

import java.util.List;

/**
 * Skill 在线编辑器视图对象：返回待编辑的 DRAFT 版本及其全部文件。
 *
 * @param skill     Skill 基本信息
 * @param versionId 当前编辑的 DRAFT 版本 ID
 * @param version   当前编辑的 DRAFT 版本号
 * @param files     DRAFT 版本全部文件（含内容）
 */
public record SkillEditorVo(SkillVo skill, Long versionId, Long version, List<SkillFileVo> files) {
}
