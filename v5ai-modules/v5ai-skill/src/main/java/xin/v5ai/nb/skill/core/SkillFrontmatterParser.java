package xin.v5ai.nb.skill.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 极简 frontmatter / YAML 键值解析：只支持顶层的 {@code key: value} 形式
 * （含引号包裹的值），用于 SKILL.md 的 YAML Front Matter 与 metadata.yaml。
 * 复杂 YAML 不在本阶段支持范围。
 */
public final class SkillFrontmatterParser {
    private SkillFrontmatterParser() {
    }

    /**
     * 解析 SKILL.md 的 YAML Front Matter：文件以 {@code ---} 开头，
     * 到下一个 {@code ---} 之间的行为键值。
     *
     * @param markdown SKILL.md 内容
     * @return 解析结果（metadata 可为空；content 为去掉 frontmatter 后的正文）
     */
    public static ParsedMarkdown parseMarkdown(String markdown) {
        if (markdown == null || !markdown.startsWith("---")) {
            return new ParsedMarkdown(Map.of(), markdown);
        }
        int lineEnd = markdown.indexOf('\n');
        if (lineEnd < 0) {
            return new ParsedMarkdown(Map.of(), markdown);
        }
        int cursor = lineEnd + 1;
        int end = markdown.indexOf("\n---", cursor);
        String body;
        if (end < 0) {
            // 没有闭合的 ---：视为无 frontmatter，整体作为正文
            return new ParsedMarkdown(Map.of(), markdown);
        }
        String frontmatter = markdown.substring(cursor, end);
        body = markdown.substring(end + 4);
        return new ParsedMarkdown(parseKeyValues(frontmatter), body.trim());
    }

    /**
     * 解析普通 YAML 键值文件（metadata.yaml）。
     */
    static Map<String, String> parseYaml(String content) {
        return parseKeyValues(content);
    }

    private static Map<String, String> parseKeyValues(String text) {
        Map<String, String> result = new LinkedHashMap<>();
        if (text == null) {
            return result;
        }
        for (String rawLine : text.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int separator = line.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (key.isEmpty()) {
                continue;
            }
            result.put(key, unquote(value));
        }
        return result;
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                || value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    public record ParsedMarkdown(Map<String, String> metadata, String content) {
    }
}
