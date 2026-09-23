package xin.v5ai.nb.common.agentscope.core.token;

import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.agentscope.core.domain.AttachmentRef;
import xin.v5ai.nb.common.agentscope.core.domain.SessionMessage;
import xin.v5ai.nb.common.agentscope.core.domain.bo.AgentRunBo;
import xin.v5ai.nb.common.agentscope.enums.MessageRole;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用量估算兜底口径：CJK 按 1 字 1 token，其余按 4 字符 1 token，图片按张计。
 *
 * <p>这组数字只在「模型没有回报真实用量」时才会被记账，但它必须比旧的「一律 /4」更接近真实，
 * 否则中文长会话的配额与用量明细会系统性低估。</p>
 */
class PromptTokenEstimatorTest {

    @Test
    void blankTextCostsNothing() {
        assertThat(PromptTokenEstimator.estimateText(null)).isZero();
        assertThat(PromptTokenEstimator.estimateText("")).isZero();
    }

    @Test
    void asciiTextIsFourCharsPerToken() {
        // 11 个字符 → ceil(11 / 4) = 3
        assertThat(PromptTokenEstimator.estimateText("hello world")).isEqualTo(3);
        assertThat(PromptTokenEstimator.estimateText("12345678")).isEqualTo(2);
    }

    @Test
    void cjkCharsCostOneTokenEach() {
        assertThat(PromptTokenEstimator.estimateText("你好世界")).isEqualTo(4);
        // 全角标点也按 1 字 1 token
        assertThat(PromptTokenEstimator.estimateText("，。！？")).isEqualTo(4);
        assertThat(PromptTokenEstimator.estimateText("こんにちは")).isEqualTo(5);
    }

    @Test
    void mixedTextAddsBothParts() {
        // 「你好」= 2，" world"（6 个 ASCII 字符）= 2
        assertThat(PromptTokenEstimator.estimateText("你好 world")).isEqualTo(4);
    }

    @Test
    void messagesCountTextAndImages() {
        var messages = List.of(
                Msg.builder().role(MsgRole.SYSTEM).textContent("你好").build(),
                Msg.builder().role(MsgRole.USER).content(List.<ContentBlock>of(
                        TextBlock.builder().text("看图").build(),
                        ImageBlock.builder().source(Base64Source.builder()
                                .mediaType("image/png").data("eA==").build()).build())).build());

        assertThat(PromptTokenEstimator.estimateMessages(messages, 1024)).isEqualTo(2 + 2 + 1024);
        // 图片折算关掉时只剩文本
        assertThat(PromptTokenEstimator.estimateMessages(messages, 0)).isEqualTo(4);
    }

    @Test
    void requestCountsQueryRagHistoryAndAttachments() {
        var request = new AgentRunBo("agent-x", "c", "问题", "RAG 片段", List.of(
                new SessionMessage("c", "agent-x", MessageRole.USER, "上一轮",
                        List.of(AttachmentRef.image(5L))),
                new SessionMessage("c", "agent-x", MessageRole.ASSISTANT, "answer")), null)
                .withAttachments(List.of(AttachmentRef.image(6L)));

        // 问题 2 + RAG 片段 3 + 上一轮 3 + 一张历史图 1024 + answer 2 + 本轮图 1024
        assertThat(PromptTokenEstimator.estimateRequest(request, 1024)).isEqualTo(2 + 3 + 3 + 1024 + 2 + 1024);
    }
}
