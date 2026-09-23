package xin.v5ai.nb.skill.core;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.common.agentscope.core.ModelChatClient;
import xin.v5ai.nb.common.agentscope.core.exception.ModelChatException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * Skill 编辑器 AI 能力：经 {@link ModelChatClient} 调用平台已配置的 Chat 模型，
 * 生成 / 优化 Skill 文件内容（非流式，返回完整文本）。
 * 系统提示词为中文，并强制 Skill 包规范（SKILL.md frontmatter 合法、单文件大小上限）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillAiGenerator {

    @Value("${v5ai.skill.ai.timeout-seconds:90}")
    private long timeoutSeconds;

    private final ModelChatClient modelChatClient;

    /**
     * 生成完整的 SKILL.md 内容：保留技能名，需求说明作为 frontmatter description。
     * 返回前校验 frontmatter 合法性（name/description/正文非空、大小上限）。
     */
    public String generateSkillMd(String skillName, String requirement, Long modelId) {
        String systemPrompt = """
                你是 AI 技能（Skill）编写专家，负责为平台生成符合规范的 SKILL.md 技能定义文件。
                技能文件格式要求：
                1. 以 YAML Front Matter 开头，必须包含 name 与 description 两个字段，且都非空；
                2. description 必须使用用户提供的需求说明原文（如有换行转为单行）；
                3. Front Matter 之后必须有一段非空的中文正文，说明该技能的作用、典型使用方式与注意事项；
                4. 只输出 SKILL.md 的完整内容（从 --- 开始到文件结束），不要输出任何解释或多余文字。
                """;
        String userPrompt = "技能名称（name，必须原样保留）：" + skillName
                + "\n需求说明（description）：" + requirement
                + "\n\n请生成完整的 SKILL.md 内容：";
        String content = chat(modelId, systemPrompt, userPrompt);
        validateSkillMd(skillName, content);
        return content;
    }

    /**
     * 优化指定文件的内容。SKILL.md 额外校验 frontmatter 合法性。
     *
     * @param filePath       包内相对路径（用于提示词与校验分支）
     * @param currentContent 当前文件内容
     * @param requirement    优化要求（必填）
     * @param direction      优化方向（可选）
     * @param skillName      技能名（SKILL.md 优化时用于校验 name 保持不变）
     */
    public String optimizeFile(String filePath, String currentContent, String requirement,
                               String direction, String skillName, Long modelId) {
        boolean isSkillMd = "SKILL.md".equals(filePath);
        String systemPrompt = """
                你是 AI 技能（Skill）文件优化助手。请根据用户的优化要求，重写提供的文件内容。
                要求：
                1. 只输出重写后的完整文件内容，不要任何解释、前言或代码块围栏；
                2. 保持文件原有格式约定；若是 SKILL.md，必须保留合法的 YAML Front Matter
                   （name、description 均非空且保持原 name 不变），正文非空；
                3. 不要改变文件路径语义，不要新增或删除文件。
                """;
        String directionPart = (direction == null || direction.isBlank()) ? "" : "\n优化方向：" + direction;
        String userPrompt = "文件路径：" + filePath
                + "\n\n当前内容：\n```\n" + currentContent + "\n```"
                + "\n\n优化要求：" + requirement
                + directionPart
                + "\n\n请输出重写后的完整文件内容：";
        String content = chat(modelId, systemPrompt, userPrompt);
        requireSize(filePath, content);
        if (isSkillMd) {
            validateSkillMd(skillName, content);
        }
        return content;
    }

    /**
     * 调用模型生成文本：非流式拼接全部响应分片后返回。
     */
    private String chat(Long modelId, String systemPrompt, String userPrompt) {
        var messages = List.of(
                Msg.builder().role(MsgRole.SYSTEM).textContent(systemPrompt).build(),
                Msg.builder().role(MsgRole.USER).textContent(userPrompt).build()
        );
        try {
            return modelChatClient.chatText(modelId, messages, Duration.ofSeconds(timeoutSeconds));
        } catch (ModelChatException exception) {
            // 模型对话契约：配置缺失 IAE / 运行期失败 ModelChatException。
            // 此处把运行期失败转成带中文文案的 IAE（与 AgentConfigGenerator 一致，
            // 且 IAE 由全局 handler 透出消息；ModelChatException 会落入通用兜底）。
            log.warn("skill ai call failed for model {}: {}", modelId, exception.getMessage());
            throw new IllegalArgumentException("AI 模型调用失败：" + exception.getMessage(), exception);
        }
    }

    /**
     * 校验生成的 SKILL.md：合法 frontmatter（name 等于技能名、description 非空）、正文非空、大小上限。
     */
    static void validateSkillMd(String expectedName, String content) {
        requireSize("SKILL.md", content);
        var parsed = SkillFrontmatterParser.parseMarkdown(content);
        String name = parsed.metadata().get("name");
        String description = parsed.metadata().get("description");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("AI 生成的 SKILL.md 缺少 name frontmatter，请重试");
        }
        if (!expectedName.equals(name.trim())) {
            throw new IllegalArgumentException("AI 生成的 SKILL.md name 与技能名不一致：" + name + "，请重试");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("AI 生成的 SKILL.md 缺少 description frontmatter，请重试");
        }
        if (parsed.content() == null || parsed.content().isBlank()) {
            throw new IllegalArgumentException("AI 生成的 SKILL.md 缺少正文，请重试");
        }
    }

    private static void requireSize(String path, String content) {
        if (content == null) {
            throw new IllegalArgumentException("AI 返回内容为空: " + path);
        }
        if (content.getBytes(StandardCharsets.UTF_8).length > SkillPackageParser.MAX_FILE_BYTES) {
            throw new IllegalArgumentException("AI 生成内容超过大小上限（"
                    + (SkillPackageParser.MAX_FILE_BYTES / 1024) + "KB）: " + path);
        }
    }
}
