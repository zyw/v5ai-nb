package xin.v5ai.nb.model.core;

import cn.hutool.core.util.StrUtil;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.GenerateOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.common.agentscope.core.ModelChatClient;
import xin.v5ai.nb.common.agentscope.core.ModelConfigRetrieve;
import xin.v5ai.nb.common.agentscope.core.domain.dto.ModelExtConfigAttrs;
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
            case TYPE_EMBEDDING -> testEmbedding(config);
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

    /**
     * EMBEDDING：发一次最小编码请求判定连通，并解析实际输出维度；
     * 与 config 声明的维度能力（{@code dimensionAdjustable}/{@code embeddingDimension}/{@code maxDimension}）
     * 比对，声明与实测不符时返回失败并给出修正指引——避免模型配置臆造可调维度、直到文档索引才炸。
     */
    private TestModelConnectionVo testEmbedding(ModelRuntimeConfigDTO config) {
        try {
            // ① 不带 dimensions 的最小请求：2xx 判定连通并取实际输出维度
            ProbeResponse plain = post(config, "/embeddings", "{\"model\":\"%s\",\"input\":\"ping\"}"
                    .formatted(escape(config.modelKey())));
            if (!plain.ok()) {
                return TestModelConnectionVo.failed(config.modelId(),
                        "EMBEDDING 探测 失败: HTTP " + plain.status() + " " + summarize(plain.body()));
            }
            Integer actualDimension = parseEmbeddingDimension(plain.body());
            ModelExtConfigAttrs declared = config.config();
            boolean adjustable = declared != null && Boolean.TRUE.equals(declared.getDimensionAdjustable());

            // ② 声明「维度可调」时验证端点确实接受 dimensions 参数（用声明上限探测一次）
            if (adjustable) {
                Integer cap = firstPositive(declared.getEmbeddingDimension(), declared.getMaxDimension());
                if (cap != null) {
                    ProbeResponse dimsProbe = post(config, "/embeddings",
                            "{\"model\":\"%s\",\"input\":\"ping\",\"dimensions\":%d}"
                                    .formatted(escape(config.modelKey()), cap));
                    if (!dimsProbe.ok()) {
                        return TestModelConnectionVo.failed(config.modelId(),
                                "EMBEDDING 探测: 模型配置声明了维度可调(上限 " + cap + ")，但上游拒绝 dimensions 参数"
                                        + "（HTTP " + dimsProbe.status() + ": " + summarize(dimsProbe.body()) + "）。"
                                        + "请关闭『维度可调』并把『嵌入维度』改为该模型实际输出维度"
                                        + (actualDimension == null ? "" : "（实测 " + actualDimension + "）")
                                        + "，或在知识库维度检查中确认支持值。");
                    }
                    // 可调探测成功：记录实际输出维度（声明上限合法即可连通，输出仍以冻结维度为准）
                }
            }
            // ③ 不可调且声明了固定维度：必须与实测输出一致，否则检索维度不匹配
            Integer declaredDimension = declared == null ? null : declared.getEmbeddingDimension();
            if (!adjustable && declaredDimension != null && actualDimension != null
                    && !declaredDimension.equals(actualDimension)) {
                return TestModelConnectionVo.failed(config.modelId(),
                        "EMBEDDING 探测: 模型实际输出维度为 " + actualDimension
                                + "，与配置声明的嵌入维度 " + declaredDimension + " 不一致。"
                                + "请将『嵌入维度』改为 " + actualDimension + "（如需降维请先确认模型支持 dimensions 参数再开启『维度可调』）。");
            }
            String detail = actualDimension == null ? "" : "（实际输出维度 " + actualDimension + "）";
            return TestModelConnectionVo.ok(config.modelId(), "connection ok" + detail);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return TestModelConnectionVo.failed(config.modelId(), "EMBEDDING 探测 被中断");
        } catch (Exception exception) {
            return TestModelConnectionVo.failed(config.modelId(), "EMBEDDING 探测 失败: " + safeMessage(exception));
        }
    }

    /** EMBEDDING / RERANK：直接向模型 HTTP 端点发最小编码/重排请求，2xx 即判定连通。 */
    private TestModelConnectionVo probe(ModelRuntimeConfigDTO config, String path, String jsonTemplate, String label) {
        try {
            ProbeResponse response = post(config, path, jsonTemplate.formatted(escape(config.modelKey())));
            if (response.ok()) {
                return TestModelConnectionVo.ok(config.modelId());
            }
            return TestModelConnectionVo.failed(config.modelId(),
                    label + " 失败: HTTP " + response.status() + " " + summarize(response.body()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return TestModelConnectionVo.failed(config.modelId(), label + " 被中断");
        } catch (Exception exception) {
            return TestModelConnectionVo.failed(config.modelId(), label + " 失败: " + safeMessage(exception));
        }
    }

    /** 发起一次带鉴权的 JSON POST；2xx 视为成功。 */
    private ProbeResponse post(ModelRuntimeConfigDTO config, String path, String jsonBody) throws Exception {
        AgentModelCredentialConfig credentials = decryptCredentials(config);
        String baseUrl = StrUtil.isBlank(credentials.baseUrl()) ? DEFAULT_BASE_URL : credentials.baseUrl();
        String url = trimTrailingSlash(baseUrl) + ensureLeadingSlash(path);
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
        return new ProbeResponse(status >= 200 && status < 300, status, response.body());
    }

    /** 解析 OpenAI 兼容 /embeddings 响应的实际输出维度（data[0].embedding 长度）；响应无向量时返回 null。 */
    private static Integer parseEmbeddingDimension(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        int idx = body.indexOf("\"embedding\"");
        if (idx < 0) {
            return null;
        }
        int open = body.indexOf('[', idx);
        if (open < 0) {
            return null;
        }
        int close = body.indexOf(']', open);
        if (close <= open) {
            return null;
        }
        String content = body.substring(open + 1, close).trim();
        if (content.isEmpty()) {
            return null;
        }
        return content.split(",").length;
    }

    private static Integer firstPositive(Integer... values) {
        for (Integer value : values) {
            if (value != null && value > 0) {
                return value;
            }
        }
        return null;
    }

    /** POST 探测结果：连通性与原始状态码/响应体。 */
    private record ProbeResponse(boolean ok, int status, String body) {
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
