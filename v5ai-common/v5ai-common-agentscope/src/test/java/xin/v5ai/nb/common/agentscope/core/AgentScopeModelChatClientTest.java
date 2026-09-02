package xin.v5ai.nb.common.agentscope.core;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import xin.v5ai.nb.common.agentscope.core.domain.dto.ModelRuntimeConfigDTO;
import xin.v5ai.nb.common.agentscope.core.exception.ModelChatException;
import xin.v5ai.nb.common.agentscope.core.factory.AgentScopeModelFactory;
import xin.v5ai.nb.common.encrypt.cipher.CredentialCipher;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link AgentScopeModelChatClient} 单测：mock gateway / cipher / factory 与假 Model 流，
 * 验证文本拼接、空响应 / 超时 / 配置缺失的映射（不经网络）。
 */
class AgentScopeModelChatClientTest {

    private static final String PLAINTEXT = "{\"apiKey\":\"k\",\"baseUrl\":\"https://api.example.com\"}";

    private final ModelRuntimeConfigGateway gateway = mock(ModelRuntimeConfigGateway.class);
    private final CredentialCipher cipher = mock(CredentialCipher.class);
    private final AgentScopeModelFactory factory = mock(AgentScopeModelFactory.class);
    private final Model model = mock(Model.class);

    private AgentScopeModelChatClient client;

    @BeforeEach
    void setUp() {
        client = new AgentScopeModelChatClient(gateway, cipher, factory);
        var config = new ModelRuntimeConfigDTO(1L, "gpt-4o", "CHAT", 1L, "openai", "OPENAI", "cipher");
        when(gateway.findRuntimeConfigByModelId(1L)).thenReturn(config);
        when(cipher.decrypt("cipher")).thenReturn(PLAINTEXT);
        when(factory.create(config, PLAINTEXT)).thenReturn(model);
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
                .hasMessageContaining("api.example.com");
    }

    @Test
    void chatTextRejectsMissingConfig() {
        when(gateway.findRuntimeConfigByModelId(2L)).thenReturn(null);

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
