package xin.v5ai.nb.model.core;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.agentscope.core.model.ChatResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.agentscope.core.ModelChatClient;
import xin.v5ai.nb.common.agentscope.core.ModelConfigRetrieve;
import xin.v5ai.nb.common.agentscope.core.domain.dto.ModelExtConfigAttrs;
import xin.v5ai.nb.common.agentscope.core.domain.dto.ModelRuntimeConfigDTO;
import xin.v5ai.nb.common.agentscope.core.exception.ModelChatException;
import xin.v5ai.nb.common.encrypt.cipher.CredentialCipher;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link AgentScopeModelConnectionTester} 单测：
 * 用本地 {@link HttpServer} 起真实端口验证 EMBEDDING/RERANK 探针的路径、鉴权与 2xx/5xx 映射；
 * CHAT 分支经 mock 的 {@link ModelChatClient} 验证。
 */
class AgentScopeModelConnectionTesterTest {

    private final List<ProbeRequest> requests = new ArrayList<>();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    // ---------- EMBEDDING ----------

    @Test
    void embeddingProbeSucceedsOn2xx() {
        startServer(exchange -> respond(exchange, 200, "{\"data\":[]}"));
        var tester = tester(serverPort());

        var result = tester.test(1L);

        assertThat(result.ok()).isTrue();
        assertThat(requests).singleElement().satisfies(req -> {
            assertThat(req.path()).isEqualTo("/embeddings");
            assertThat(req.authorization()).isEqualTo("Bearer sk-test");
            assertThat(req.body()).contains("\"model\":\"text-embedding-3-small\"");
        });
    }

    @Test
    void embeddingProbeFailsOnErrorStatus() {
        startServer(exchange -> respond(exchange, 500, "boom detail"));
        var tester = tester(serverPort());

        var result = tester.test(1L);

        assertThat(result.ok()).isFalse();
        assertThat(result.message()).contains("EMBEDDING 探测 失败: HTTP 500", "boom detail");
    }

    // ---------- EMBEDDING 维度探测与声明校验 ----------

    @Test
    void embeddingProbeFlagsDeclaredDimensionMismatchWithActual() {
        // 模型声明固定嵌入维度 3072，上游实际输出 2048 维 → 测试失败并给出修正指引
        startServer(exchange -> respond(exchange, 200, embeddingBody(2048)));
        var attrs = new ModelExtConfigAttrs();
        attrs.setEmbeddingDimension(3072);
        var tester = testerWith(config(1L, "text-embedding-3-small", "EMBEDDING", attrs), serverPort());

        var result = tester.test(1L);

        assertThat(result.ok()).isFalse();
        assertThat(result.message()).contains("实际输出维度为 2048", "声明的嵌入维度 3072 不一致", "改为 2048");
    }

    @Test
    void embeddingProbeFlagsAdjustableClaimWhenUpstreamRejectsDimensions() {
        // 模型声明 dimensionAdjustable=true + 上限 3072，但上游拒绝 dimensions 参数（与 doubao 事故同构）
        startServer(exchange -> {
            String body = latestRequestBody();
            if (body.contains("dimensions")) {
                respond(exchange, 400,
                        "{\"error\":{\"code\":\"InvalidParameter\",\"param\":\"dimensions\",\"type\":\"BadRequest\"}}");
            } else {
                respond(exchange, 200, embeddingBody(2));
            }
        });
        var attrs = new ModelExtConfigAttrs();
        attrs.setDimensionAdjustable(true);
        attrs.setMaxDimension(3072);
        var tester = testerWith(config(1L, "doubao-embedding-vision", "EMBEDDING", attrs), serverPort());

        var result = tester.test(1L);

        assertThat(result.ok()).isFalse();
        assertThat(result.message()).contains("声明了维度可调(上限 3072)", "上游拒绝 dimensions 参数", "关闭『维度可调』");
    }

    @Test
    void embeddingProbeSucceedsWhenDeclarationMatchesActual() {
        // 声明固定维度与实际输出一致 → 连通成功并回传实测维度
        startServer(exchange -> respond(exchange, 200, embeddingBody(2048)));
        var attrs = new ModelExtConfigAttrs();
        attrs.setEmbeddingDimension(2048);
        var tester = testerWith(config(1L, "doubao-embedding-vision", "EMBEDDING", attrs), serverPort());

        var result = tester.test(1L);

        assertThat(result.ok()).isTrue();
        assertThat(result.message()).contains("实际输出维度 2048");
    }

    /** 最近一次请求的 body（startServer 已把原始 body 记入 {@link #requests}）。 */
    private String latestRequestBody() {
        return requests.isEmpty() ? "" : requests.get(requests.size() - 1).body();
    }

    /** 生成指定维度的 OpenAI 兼容 /embeddings 响应体。 */
    private static String embeddingBody(int dimension) {
        return "{\"data\":[{\"embedding\":[" + "0.1,".repeat(dimension - 1) + "0.1]}]}";
    }

    // ---------- RERANK ----------

    @Test
    void rerankProbeUsesConfiguredRerankPath() {
        startServer(exchange -> respond(exchange, 200, "{\"results\":[]}"));
        var attrs = new ModelExtConfigAttrs();
        attrs.setRerankPath("/rerank/v1");
        var tester = testerWith(config(2L, "qwen-rerank-v3", "RERANK", attrs), serverPort());

        var result = tester.test(2L);

        assertThat(result.ok()).isTrue();
        assertThat(requests).singleElement().satisfies(req -> {
            assertThat(req.path()).isEqualTo("/rerank/v1");
            assertThat(req.body()).contains("\"documents\":[\"ping\"]");
        });
    }

    @Test
    void rerankProbeDefaultsToSlashRerankPath() {
        startServer(exchange -> respond(exchange, 200, "{\"results\":[]}"));
        var tester = testerWith(config(2L, "qwen-rerank-v3", "RERANK", null), serverPort());

        var result = tester.test(2L);

        assertThat(result.ok()).isTrue();
        assertThat(requests).singleElement().satisfies(req -> assertThat(req.path()).isEqualTo("/rerank"));
    }

    // ---------- CHAT ----------

    @Test
    void chatProbeDelegatesToChatClientAndSucceeds() throws Exception {
        var chatClient = mock(ModelChatClient.class);
        when(chatClient.firstResponse(any(), any(), any(), any())).thenReturn(mock(ChatResponse.class));
        var tester = new AgentScopeModelConnectionTester(chatClient, configRetrieveReturning(
                config(1L, "gpt-4o", "CHAT", null)), mock(CredentialCipher.class), 5000);

        var result = tester.test(1L);

        assertThat(result.ok()).isTrue();
    }

    @Test
    void chatProbeMapsFailureToFailed() throws Exception {
        var chatClient = mock(ModelChatClient.class);
        when(chatClient.firstResponse(any(), any(), any(), any()))
                .thenThrow(new ModelChatException("model chat timed out"));
        var tester = new AgentScopeModelConnectionTester(chatClient, configRetrieveReturning(
                config(1L, "gpt-4o", "CHAT", null)), mock(CredentialCipher.class), 5000);

        var result = tester.test(1L);

        assertThat(result.ok()).isFalse();
        assertThat(result.message()).contains("model chat timed out");
    }

    // ---------- 异常路径 ----------

    @Test
    void missingRuntimeConfigFails() {
        var configRetrieve = mock(ModelConfigRetrieve.class);
        when(configRetrieve.findRuntimeConfigByModelId(1L)).thenReturn(null);
        var tester = new AgentScopeModelConnectionTester(mock(ModelChatClient.class), configRetrieve,
                mock(CredentialCipher.class), 5000);

        var result = tester.test(1L);

        assertThat(result.ok()).isFalse();
        assertThat(result.message()).contains("not found");
    }

    @Test
    void unsupportedModelTypeFails() {
        var tester = new AgentScopeModelConnectionTester(mock(ModelChatClient.class),
                configRetrieveReturning(config(1L, "x", "IMAGE", null)),
                mock(CredentialCipher.class), 5000);

        var result = tester.test(1L);

        assertThat(result.ok()).isFalse();
        assertThat(result.message()).contains("unsupported model type");
    }

    // ---------- 基建 ----------

    private void startServer(Consumer<HttpExchange> responder) {
        try {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                requests.add(new ProbeRequest(exchange.getRequestURI().getPath(),
                        exchange.getRequestHeaders().getFirst("Authorization"), body));
                responder.accept(exchange);
            });
            server.start();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private int serverPort() {
        return server.getAddress().getPort();
    }

    private AgentScopeModelConnectionTester tester(int port) {
        return testerWith(config(1L, "text-embedding-3-small", "EMBEDDING", null), port);
    }

    private AgentScopeModelConnectionTester testerWith(ModelRuntimeConfigDTO dto, int port) {
        var credentials = mock(CredentialCipher.class);
        try {
            when(credentials.decrypt("cipher")).thenReturn(
                    "{\"apiKey\":\"sk-test\",\"baseUrl\":\"http://127.0.0.1:" + port + "\"}");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return new AgentScopeModelConnectionTester(mock(ModelChatClient.class),
                configRetrieveReturning(dto), credentials, 5000);
    }

    /** 单模型场景：所有 test(modelId) 都命中同一个 runtime 配置。 */
    private ModelConfigRetrieve configRetrieveReturning(ModelRuntimeConfigDTO dto) {
        var configRetrieve = mock(ModelConfigRetrieve.class);
        when(configRetrieve.findRuntimeConfigByModelId(any())).thenReturn(dto);
        return configRetrieve;
    }

    private ModelRuntimeConfigDTO config(Long id, String modelKey, String modelType, ModelExtConfigAttrs attrs) {
        return new ModelRuntimeConfigDTO(id, modelKey, modelType, 3L, "provider", "adapter", "cipher", attrs);
    }

    private static void respond(HttpExchange exchange, int code, String body) {
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            exchange.close();
        }
    }

    private record ProbeRequest(String path, String authorization, String body) {
    }
}
