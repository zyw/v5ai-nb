package xin.v5ai.nb.skill.domain.vo;

/**
 * Skill 使用情况视图对象：当前被多少 Agent 绑定。
 *
 * @param count 绑定该 Skill 的 Agent 数量
 */
public record SkillUsageVo(long count) {
}
