package xin.v5ai.nb.skill;

/**
 * Skill 包文件：发布版本的原始文件（SKILL.md、prompts/、resources/ 等），
 * 运行时据此做 Workspace 注入。
 *
 * @param id        主键
 * @param skillId   所属 Skill
 * @param versionId 所属版本
 * @param path      包内相对路径（如 "SKILL.md"、"prompts/guide.md"）
 * @param content   文本内容（UTF-8）
 */
public record SkillFile(
        Long id,
        Long skillId,
        Long versionId,
        String path,
        String content
) {
}
