package xin.v5ai.nb.skill.domain.bo;

/**
 * 在线新建 Skill 请求体。
 *
 * @param name               技能名称（全局唯一）
 * @param description        技能描述（写入 skill.description，并生成到 SKILL.md frontmatter）
 * @param versionDescription 版本描述（可选，写入 DRAFT v1 的 skill_version.description，与上传弹窗一致）
 */
public record CreateSkillBo(String name, String description, String versionDescription) {
}
