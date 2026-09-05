package xin.v5ai.nb.model.core;

import cn.hutool.core.util.StrUtil;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.GenerateOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.common.agentscope.core.ModelChatClient;
import xin.v5ai.nb.common.agentscope.core.ModelConfigRetrieve;
import xin.v5ai.nb.common.agentscope.core.domain.dto.ModelRuntimeConfigDTO;
import xin.v5ai.nb.common.agentscope.core.exception.ModelChatException;
import xin.v5ai.nb.common.agentscope.core.factory.config.AgentModelCredentialConfig;
import xin.v5ai.nb.common.encrypt.cipher.CredentialCipher;
import xin.v5ai.nb.model.domain.vo.TestModelConnectionVo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * 模型连通性测试：按模型类型分发探针。
 * <ul>
 *   <li>CHAT：经 {@link ModelChatClient} 发起一次最小对话，取到首个响应块即判定连通；</li>
 *   <li>EMBEDDING：向 {@code baseUrl + /embeddings} 发一次最小编码请求（input=ping），2xx 判定连通；</li>
 *   <li>RERANK：向 {@code baseUrl + rerankPath}（config 未配时缺省 /rerank）发一次最小重排请求，2xx 判定连通。</li>
 * </ul>
 * 失败时返回带原因的 {@link TestModelConnectionVo#failed}，不抛未捕获异常。
 */
@Component
public class AgentScopeModelConnectionTester implements ModelConnectionTester {

    private static final String TYPE_CHAT = "CHAT";
    private static final String TYPE_EMBEDDING = "EMBEDDING";
    private static final String TYPE_RERANK = "RERANK";

    /** baseUrl 未配置时回退的默认 OpenAI 兼容地址（与 embedding 运行时一致）。 */
    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    /** RERANK 模型 config.rerankPath 未配置时的缺省重排路径。 */
    private static final String DEFAULT_RERANK_PATH = "/rerank";

    private final ModelChatClient modelChatClient;
    private final ModelConfigRetrieve configRetrieve;
    private final CredentialCipher credentialCipher;
    private final Duration timeout;

    public AgentScopeModelConnectionTester(
            ModelChatClient modelChatClient,
            ModelConfigRetrieve configRetrieve,
            CredentialCipher credentialCipher,
            @Value("${model.connection-test.timeout-millis:10000}") long timeoutMillis
    ) {
        this.modelChatClient = modelChatClient;
        this.configRetrieve = configRetrieve;
        this.credentialCipher = credentialCipher;
        this.timeout = Duration.ofMillis(timeoutMillis);
    }

    @Override
    public TestModelConnectionVo test(Long modelId) {
        ModelRuntimeConfigDTO config = configRetrieve.findRuntimeConfigByModelId(modelId);
        if (config == null) {
            return TestModelConnectionVo.failed(modelId, "model runtime config not found: " + modelId);
        }
        return switch (config.modelType()) {
            case TYPE_CHAT -> testChat(config);
            case TYPE_EMBEDDING -> probe(config, "/embeddings",
                    "{\"model\":\"%s\",\"input\":\"ping\"}", "EMBEDDING 探测");
            case TYPE_RERANK -> probe(config, rerankPath(config),
                    "{\"model\":\"%s\",\"query\":\"ping\",\"documents\":[\"ping\"]}", "RERANK 探测");
            default -> TestModelConnectionVo.failed(modelId,
                    "unsupported model type for connection test: " + config.modelType());
        };
    }

    /** CHAT：复用对话端口，首个响应块即连通。 */
    private TestModelConnectionVo testChat(ModelRuntimeConfigDTO config) {
        var ping = Msg.builder().role(MsgRole.USER).textContent("ping").build();
        try {
            modelChatClient.firstResponse(config.modelId(), List.of(ping),
                    GenerateOptions.builder().maxTokens(5).build(), timeout);
            return TestModelConnectionVo.ok(config.modelId());
        } catch (ModelChatException exception) {
            return TestModelConnectionVo.failed(config.modelId(), safeMessage(exception));
        } catch (RuntimeException exception) {
            return TestModelConnectionVo.failed(config.modelId(), safeMessage(exception));
        }
    }

    /** EMBEDDING / RERANK：直接向模型 HTTP 端点发最小编码/重排请求，2xx 即判定连通。 */
    private TestModelConnectionVo probe(ModelRuntimeConfigDTO config, String path, String jsonTemplate, String label) {
        try {
            AgentModelCredentialConfig credentials = decryptCredentials(config);
            String baseUrl = StrUtil.isBlank(credentials.baseUrl()) ? DEFAULT_BASE_URL : credentials.baseUrl();
            String url = trimTrailingSlash(baseUrl) + ensureLeadingSlash(path);
            String jsonBody = jsonTemplate.formatted(escape(config.modelKey()));
            var client = HttpClient.newBuilder().connectTimeout(timeout).build();
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + (credentials.apiKey() == null ? "" : credentials.apiKey()))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return TestModelConnectionVo.ok(config.modelId());
            }
            return TestModelConnectionVo.failed(config.modelId(),
                    label + " 失败: HTTP " + status + " " + summarize(response.body()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return TestModelConnectionVo.failed(config.modelId(), label + " 被中断");
        } catch (Exception exception) {
            return TestModelConnectionVo.failed(config.modelId(), label + " 失败: " + safeMessage(exception));
        }
    }

    /** 解密并解析凭据；失败按连接失败返回可读原因。 */
    private AgentModelCredentialConfig decryptCredentials(ModelRuntimeConfigDTO config) {
        try {
            return AgentModelCredentialConfig.parse(
                    credentialCipher.decrypt(config.credentialsCiphertext()));
        } catch (Exception exception) {
            throw new IllegalArgumentException("模型凭据解析失败: " + safeMessage(exception));
        }
    }

    private String rerankPath(ModelRuntimeConfigDTO config) {
        if (config.config() != null && StrUtil.isNotBlank(config.config().getRerankPath())) {
            return config.config().getRerankPath().trim();
        }
        return DEFAULT_RERANK_PATH;
    }

    private static String trimTrailingSlash(String url) {
        return url.replaceAll("/+$", "");
    }

    private static String ensureLeadingSlash(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }

    /** 简化为单行摘要，超长截断，避免把 provider 原始大 body 灌进消息。 */
    private static String summarize(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String cleaned = body.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 120 ? cleaned.substring(0, 120) + "…" : cleaned;
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank()
                ? throwable.getClass().getSimpleName()
                : message.replaceAll("\\s+", " ").trim();
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
