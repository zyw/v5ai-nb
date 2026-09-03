package xin.v5ai.nb.common.agentscope.core;

import io.agentscope.core.message.TextBlock;
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
}
