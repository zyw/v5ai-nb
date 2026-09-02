package xin.v5ai.nb.skill.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SkillAiGenerator#validateSkillMd} 的校验逻辑单测。
 */
class SkillAiGeneratorTest {

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
