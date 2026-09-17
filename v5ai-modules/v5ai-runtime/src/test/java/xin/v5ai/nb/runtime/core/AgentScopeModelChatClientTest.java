package xin.v5ai.nb.runtime.core;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import xin.v5ai.nb.common.agentscope.core.exception.ModelChatException;
import xin.v5ai.nb.common.agentscope.core.resolver.AgentModelResolver;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link AgentScopeModelChatClient} 单测：mock {@link AgentModelResolver}（模型解析唯一接缝）与假 Model 流，
 * 验证文本拼接、空响应 / 超时 / 解析失败映射（不经网络、不复刻解析链）。
 */
class AgentScopeModelChatClientTest {

    private final AgentModelResolver resolver = mock(AgentModelResolver.class);
    private final Model model = mock(Model.class);

    private AgentScopeModelChatClient client;

    @BeforeEach
    void setUp() {
        when(resolver.resolve(1L)).thenReturn(model);
        when(model.getModelName()).thenReturn("gpt-4o");
        client = new AgentScopeModelChatClient(resolver);
    }

    @Test
    void chatTextJoinsAllTextBlocks() {
        when(model.stream(anyList(), anyList(), any())).thenReturn(Flux.just(
                response("你"), response("好")));

        String text = client.chatText(1L, List.of(userMsg("hi")), Duration.ofSeconds(10));

        assertThat(text).isEqualTo("你好");
    }

    @Test
    void chatTextRejectsEmptyStream() {
        when(model.stream(anyList(), anyList(), any())).thenReturn(Flux.empty());

        assertThatThrownBy(() -> client.chatText(1L, List.of(userMsg("hi")), Duration.ofSeconds(10)))
                .isInstanceOf(ModelChatException.class)
                .hasMessageContaining("returned no response");
    }

    @Test
    void chatTextRejectsEmptyContent() {
        // 有响应但没有任何 TextBlock 文本
        when(model.stream(anyList(), anyList(), any()))
                .thenReturn(Flux.just(ChatResponse.builder().content(List.of()).build()));

        assertThatThrownBy(() -> client.chatText(1L, List.of(userMsg("hi")), Duration.ofSeconds(10)))
                .isInstanceOf(ModelChatException.class)
                .hasMessageContaining("empty content");
    }

    @Test
    void chatTextMapsTimeoutToMessageWithTarget() {
        when(model.stream(anyList(), anyList(), any()))
                .thenThrow(new RuntimeException("Timeout on blocking read from reactor"));

        assertThatThrownBy(() -> client.chatText(1L, List.of(userMsg("hi")), Duration.ofSeconds(10)))
                .isInstanceOf(ModelChatException.class)
                .hasMessageContaining("timed out")
                .hasMessageContaining("gpt-4o")
                .hasMessageContaining("modelId 1");
    }

    @Test
    void chatTextPropagatesResolverFailure() {
        when(resolver.resolve(2L)).thenThrow(new IllegalArgumentException("model runtime config not found: 2"));

        assertThatThrownBy(() -> client.chatText(2L, List.of(userMsg("hi")), Duration.ofSeconds(10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void firstResponseReturnsFirstChunkOnly() {
        var first = response("A");
        var second = response("B");
        when(model.stream(anyList(), anyList(), any())).thenReturn(Flux.just(first, second));

        ChatResponse got = client.firstResponse(1L, List.of(userMsg("ping")),
                GenerateOptions.builder().maxTokens(5).build(), Duration.ofSeconds(10));

        assertThat(got).isSameAs(first);
    }

    @Test
    void firstResponseRejectsEmptyStream() {
        when(model.stream(anyList(), anyList(), any())).thenReturn(Flux.empty());

        assertThatThrownBy(() -> client.firstResponse(1L, List.of(userMsg("ping")),
                GenerateOptions.builder().maxTokens(5).build(), Duration.ofSeconds(10)))
                .isInstanceOf(ModelChatException.class)
                .hasMessageContaining("returned no response");
    }

    private static Msg userMsg(String text) {
        return Msg.builder().role(MsgRole.USER).textContent(text).build();
    }

    private static ChatResponse response(String text) {
        return ChatResponse.builder()
                .content(List.of(TextBlock.builder().text(text).build()))
                .build();
    }
}
