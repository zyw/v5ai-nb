package xin.v5ai.nb.skill.domain.bo;

/**
 * Skill 文件内容请求体（在线编辑器新建/更新）。
 *
 * @param filePath 包内相对路径（如 "SKILL.md"、"prompts/guide.md"）
 * @param content  UTF-8 文本内容
 */
public record SkillFileBo(String filePath, String content) {
}
