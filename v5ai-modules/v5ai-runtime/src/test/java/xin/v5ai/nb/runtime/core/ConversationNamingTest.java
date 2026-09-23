package xin.v5ai.nb.runtime.core;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.runtime.core.utils.ConversationNaming;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 会话命名规则：兜底名取首条提问的首行、模型标题要清洗；
 * 两者都不许越过 {@code v5ai_conversation.name VARCHAR(100)}，也不许把 emoji 切成半个。
 */
class ConversationNamingTest {

    @Test
    void blankQuestionsLeaveTheConversationUnnamed() {
        assertThat(ConversationNaming.fromQuery(null)).isNull();
        assertThat(ConversationNaming.fromQuery("   ")).isNull();
        // 首行是空的（提问以换行开头）同样不命名，否则会存一条看起来像空白的名字
        assertThat(ConversationNaming.fromQuery("\n第二行才有字")).isNull();
    }

    @Test
    void onlyTheFirstLineIsUsed() {
        assertThat(ConversationNaming.fromQuery("帮我看看这张图\n顺便解释一下")).isEqualTo("帮我看看这张图");
    }

    @Test
    void whitespaceIsCollapsed() {
        assertThat(ConversationNaming.fromQuery("  这个   会话\t在问什么  ")).isEqualTo("这个 会话 在问什么");
    }

    @Test
    void exactlyAtTheLimitIsKeptAsIs() {
        var atLimit = "问".repeat(ConversationNaming.MAX_NAME_LENGTH);

        assertThat(ConversationNaming.fromQuery(atLimit)).isEqualTo(atLimit);
    }

    @Test
    void overlongQuestionsAreTruncatedToTheColumnWidth() {
        var name = ConversationNaming.fromQuery("问".repeat(150));

        assertThat(name.codePointCount(0, name.length())).isEqualTo(ConversationNaming.MAX_NAME_LENGTH);
        assertThat(name).endsWith("…");
    }

    /** emoji 是代理对：按 {@code String.length()} 截断会切出半个字符。 */
    @Test
    void truncationNeverSplitsASurrogatePair() {
        var name = ConversationNaming.fromQuery("🙂".repeat(120));

        assertThat(name.codePointCount(0, name.length())).isEqualTo(ConversationNaming.MAX_NAME_LENGTH);
        // 省略号前面的那个 char 必须是 emoji 的**低**代理项：说明最后一对没被截断
        assertThat(Character.isLowSurrogate(name.charAt(name.length() - 2))).isTrue();
    }

    @Test
    void modelTitlesAreCleanedBeforeTheyAreStored() {
        assertThat(ConversationNaming.cleanModelTitle("标题：图片内容识别。", 30)).isEqualTo("图片内容识别");
        assertThat(ConversationNaming.cleanModelTitle("  \"季度复盘\"\n", 30)).isEqualTo("季度复盘");
        assertThat(ConversationNaming.cleanModelTitle("《会话命名》", 30)).isEqualTo("会话命名");
        assertThat(ConversationNaming.cleanModelTitle("title: quarterly review", 30))
                .isEqualTo("quarterly review");
    }

    @Test
    void aModelAnswerWithoutUsableTextKeepsTheFallbackName() {
        assertThat(ConversationNaming.cleanModelTitle(null, 30)).isNull();
        assertThat(ConversationNaming.cleanModelTitle("   ", 30)).isNull();
        assertThat(ConversationNaming.cleanModelTitle("。。。", 30)).isNull();
        assertThat(ConversationNaming.cleanModelTitle("\"\"", 30)).isNull();
    }

    @Test
    void modelTitlesAreTruncatedToTheConfiguredLength() {
        var title = ConversationNaming.cleanModelTitle("问".repeat(50), 10);

        assertThat(title.codePointCount(0, title.length())).isEqualTo(10);
    }

    @Test
    void thePromptOnlyGetsTheCollapsedQuestion() {
        assertThat(ConversationNaming.questionForTitle("  第一行\n第二行  ")).isEqualTo("第一行 第二行");
        assertThat(ConversationNaming.questionForTitle("   ")).isNull();
        assertThat(ConversationNaming.questionForTitle(null)).isNull();
    }

    @Test
    void thePromptIsCappedSoLongQuestionsDoNotCostExtraTokens() {
        var prompt = ConversationNaming.questionForTitle("问".repeat(900));

        assertThat(prompt.codePointCount(0, prompt.length())).isEqualTo(500);
    }
}
