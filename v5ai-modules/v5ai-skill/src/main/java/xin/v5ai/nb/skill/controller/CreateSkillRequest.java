package xin.v5ai.nb.skill.controller;

/**
 * 在线新建 Skill 请求体。
 *
 * @param name        技能名称（全局唯一）
 * @param description 技能描述
 */
public record CreateSkillRequest(String name, String description) {
}
