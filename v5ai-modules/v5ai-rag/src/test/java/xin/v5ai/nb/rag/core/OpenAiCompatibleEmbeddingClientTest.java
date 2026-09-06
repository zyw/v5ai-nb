package xin.v5ai.nb.rag.core;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.agentscope.core.ModelConfigRetrieve;
import xin.v5ai.nb.common.agentscope.core.domain.dto.ModelExtConfigAttrs;
import xin.v5ai.nb.common.agentscope.core.domain.dto.ModelRuntimeConfigDTO;
import xin.v5ai.nb.common.encrypt.cipher.CredentialCipher;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link OpenAiCompatibleEmbeddingClient} 行为验证：
 * <ul>
 *   <li>上游对 {@code dimensions} 参数返回 400（InvalidParameter param=dimensions，doubao 场景）时，
 *       抛出的错误应包含可操作的修正指引，而非只有裸上游 body —— 该消息会原样落入
 *       {@code v5ai_knowledge_document.error_message} 供运维定位；</li>
 *   <li>连通成功时正确解析向量。</li>
 * </ul>
 */
class OpenAiCompatibleEmbeddingClientTest {

    private final List<String> requestBodies = new ArrayList<>();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void embedSurfacesActionableMessageWhenUpstreamRejectsDimensions() {
        // 可调声明（maxDimension=3072）+ KB 冻结 1536 → 客户端携带 dimensions=1536 → 上游 400（doubao 复刻）
        startServer(exchange -> respond(exchange, 400, """
                {"error":{"code":"InvalidParameter",\
                "message":"One or more parameters specified in the request are not valid. Request id: 0217...",\
                "param":"dimensions","type":"BadRequest"}}
                """));
        var attrs = new ModelExtConfigAttrs();
        attrs.setDimensionAdjustable(true);
        attrs.setMaxDimension(3072);
        var client = clientWith(config(1L, "doubao-embedding-vision", attrs));

        assertThatThrownBy(() -> client.embed("hello", 1L, 1536))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not accept the dimensions=1536 parameter")
                .hasMessageContaining("dimensionAdjustable=true/maxDimension")
                .hasMessageContaining("Fix: edit the model (id=1)")
                .hasMessageContaining("400")
                .hasMessageContaining("InvalidParameter");
        assertThat(requestBodies).singleElement().satisfies(body ->
                assertThat(body).contains("\"dimensions\":1536"));
    }

    @Test
    void embedParsesVectorOnSuccess() {
        startServer(exchange -> respond(exchange, 200,
                "{\"data\":[{\"embedding\":[0.1,0.2,0.3]}]}"));
        var client = clientWith(config(1L, "text-embedding-3-small", null));

        var vector = client.embed("hello", 1L, null);

        assertThat(vector).containsExactly(0.1f, 0.2f, 0.3f);
    }

    // ---------- 基建 ----------

    private void startServer(Consumer<HttpExchange> responder) {
        try {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/", exchange -> {
                requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                responder.accept(exchange);
            });
            server.start();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private OpenAiCompatibleEmbeddingClient clientWith(ModelRuntimeConfigDTO dto) {
        var configRetrieve = mock(ModelConfigRetrieve.class);
        when(configRetrieve.findRuntimeConfigByModelId(any())).thenReturn(dto);
        var credentials = mock(CredentialCipher.class);
        try {
            when(credentials.decrypt("cipher")).thenReturn(
                    "{\"apiKey\":\"sk-test\",\"baseUrl\":\"http://127.0.0.1:" + server.getAddress().getPort() + "\"}");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return new OpenAiCompatibleEmbeddingClient(configRetrieve, credentials);
    }

    private static ModelRuntimeConfigDTO config(Long id, String modelKey, ModelExtConfigAttrs attrs) {
        return new ModelRuntimeConfigDTO(id, modelKey, "EMBEDDING", 3L, "provider", "openai-compatible", "cipher", attrs);
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
}
