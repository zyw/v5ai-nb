package xin.v5ai.nb.skill.core;

import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.agentscope.core.ModelChatClient;
import xin.v5ai.nb.common.agentscope.core.exception.ModelChatException;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SkillAiGenerator} 单测：校验逻辑 + 经假 {@link ModelChatClient} 的生成/优化路径
 * （接口即测试面：提示词、校验与调用链在无模型下可直达）。
 */
class SkillAiGeneratorTest {

    /** 返回固定内容的假模型对话 adapter。 */
    private static SkillAiGenerator generatorReturning(String content) {
        return new SkillAiGenerator(new ModelChatClient() {
            @Override
            public String chatText(Long modelId, List<Msg> messages, Duration timeout) {
                return content;
            }

            @Override
            public ChatResponse firstResponse(Long modelId, List<Msg> messages,
                                              GenerateOptions options, Duration timeout) {
                throw new UnsupportedOperationException();
            }
        });
    }

    @Test
    void generateSkillMdRunsThroughModelChatClient() {
        var generator = generatorReturning("---\nname: weather\ndescription: 查询天气\n---\n正文内容");

        String content = generator.generateSkillMd("weather", "查询天气", 1L);

        assertThat(content).contains("name: weather").contains("查询天气");
    }

    @Test
    void generateSkillMdWrapsModelFailureInChineseMessage() {
        var generator = new SkillAiGenerator(new ModelChatClient() {
            @Override
            public String chatText(Long modelId, List<Msg> messages, Duration timeout) {
                throw new ModelChatException("model chat timed out contacting openai/gpt-4o");
            }

            @Override
            public ChatResponse firstResponse(Long modelId, List<Msg> messages,
                                              GenerateOptions options, Duration timeout) {
                throw new UnsupportedOperationException();
            }
        });

        assertThatThrownBy(() -> generator.generateSkillMd("weather", "查询天气", 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AI 模型调用失败")
                .hasMessageContaining("timed out");
    }

    @Test
    void optimizeFilePassesThroughNonMarkdownContent() {
        var generator = generatorReturning("# 更简洁的指南");

        String content = generator.optimizeFile("prompts/guide.md", "# 指南",
                "更简洁", "中文", "weather", 1L);

        assertThat(content).isEqualTo("# 更简洁的指南");
    }

    @Test
    void acceptsValidFrontmatter() {
        assertThatCode(() -> SkillAiGenerator.validateSkillMd("weather",
                "---\nname: weather\ndescription: 查询天气\n---\n正文内容"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingFrontmatter() {
        assertThatThrownBy(() -> SkillAiGenerator.validateSkillMd("weather", "没有 frontmatter 的正文"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void rejectsNameMismatch() {
        assertThatThrownBy(() -> SkillAiGenerator.validateSkillMd("weather",
                "---\nname: other\ndescription: x\n---\n正文"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不一致");
    }

    @Test
    void rejectsBlankDescription() {
        assertThatThrownBy(() -> SkillAiGenerator.validateSkillMd("weather",
                "---\nname: weather\ndescription:   \n---\n正文"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("description");
    }

    @Test
    void rejectsBlankBody() {
        assertThatThrownBy(() -> SkillAiGenerator.validateSkillMd("weather",
                "---\nname: weather\ndescription: x\n---\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("正文");
    }

    @Test
    void rejectsOversizedContent() {
        String oversized = "---\nname: weather\ndescription: x\n---\n"
                + "a".repeat(SkillPackageParser.MAX_FILE_BYTES);
        assertThatThrownBy(() -> SkillAiGenerator.validateSkillMd("weather", oversized))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("大小上限");
    }
}
