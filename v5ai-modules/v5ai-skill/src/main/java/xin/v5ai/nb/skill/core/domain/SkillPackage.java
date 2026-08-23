package xin.v5ai.nb.skill.core.domain;

import java.util.Map;

/**
 * 解析后的 Skill 包。
 *
 * @param name        技能名（SKILL.md frontmatter，必填）
 * @param description 技能描述（frontmatter，必填）
 * @param metadata    其余 frontmatter / metadata.yaml 元数据（可为空）
 * @param files       包内全部文件：相对路径 → UTF-8 文本内容（含 SKILL.md）
 */
public record SkillPackage(
        String name,
        String description,
        Map<String, String> metadata,
        Map<String, String> files
) {
    public String skillMarkdown() {
        return files.get("SKILL.md");
    }
}
