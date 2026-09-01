package xin.v5ai.nb.skill.domain.vo;

/**
 * Skill 文件视图对象（在线编辑器使用，携带完整内容）。
 *
 * @param filePath 包内相对路径
 * @param content  UTF-8 文本内容
 */
public record SkillFileVo(String filePath, String content) {
}
