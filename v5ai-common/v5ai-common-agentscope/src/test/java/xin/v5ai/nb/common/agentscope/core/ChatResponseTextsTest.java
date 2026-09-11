package xin.v5ai.nb.common.agentscope.core;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.model.ChatResponse;
import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.agentscope.utils.ChatResponseTexts;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ChatResponseTexts} 共享文本提取工具单测。
 */
class ChatResponseTextsTest {

    @Test
    void textOfJoinsAllTextBlocks() {
        var response = ChatResponse.builder().content(List.of(
                TextBlock.builder().text("你").build(),
                TextBlock.builder().text("好").build())).build();

        assertThat(ChatResponseTexts.textOf(response)).isEqualTo("你好");
    }

    @Test
    void textOfReturnsNullForNullResponseOrContent() {
        assertThat(ChatResponseTexts.textOf(null)).isNull();
        assertThat(ChatResponseTexts.textOf(ChatResponse.builder().content(null).build())).isNull();
    }

    @Test
    void textOfReturnsEmptyForContentWithoutTextBlocks() {
        var response = ChatResponse.builder().content(List.of()).build();

        assertThat(ChatResponseTexts.textOf(response)).isEmpty();
    }

    @Test
    void thinkingOfExtractsReasoningBlocksOnly() {
        var response = ChatResponse.builder().content(List.of(
                ThinkingBlock.builder().thinking("先看资料，").build(),
                ThinkingBlock.builder().thinking("再作答").build(),
                TextBlock.builder().text("回答").build())).build();

        assertThat(ChatResponseTexts.thinkingOf(response)).isEqualTo("先看资料，再作答");
        assertThat(ChatResponseTexts.textOf(response)).isEqualTo("回答");
    }

    @Test
    void thinkingOfReturnsNullForNullResponseOrContent() {
        assertThat(ChatResponseTexts.thinkingOf(null)).isNull();
        assertThat(ChatResponseTexts.thinkingOf(ChatResponse.builder().content(null).build())).isNull();
    }
}
