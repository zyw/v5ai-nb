package xin.v5ai.nb.common.agentscope.core.domain;

import java.util.Map;

/**
 * 运行时注入的已发布 Skill。
 *
 * @param skillId     技能 ID（请求侧的 {@code disabledSkillIds} 按它对账）
 * @param skillName   技能名
 * @param description 技能描述
 * @param files       当前发布版本的完整文件（相对路径 → 文本内容，含 SKILL.md）
 * @param versionId   当前发布版本 ID
 */
public record ResolvedSkill(
        Long skillId,
        String skillName,
        String description,
        Map<String, String> files,
        Long versionId
) {
}
