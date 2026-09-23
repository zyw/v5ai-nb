package xin.v5ai.nb.rag.core;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import xin.v5ai.nb.common.agentscope.core.ModelConfigRetrieve;
import xin.v5ai.nb.common.agentscope.core.domain.dto.ModelExtConfigAttrs;
import xin.v5ai.nb.common.agentscope.core.domain.dto.ModelRuntimeConfigDTO;
import xin.v5ai.nb.common.agentscope.core.factory.config.AgentModelCredentialConfig;
import xin.v5ai.nb.common.encrypt.cipher.CredentialCipher;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI-compatible {@code /embeddings} 客户端。
 * <p>
 * 按知识库绑定的嵌入模型（{@code embeddingModelId}）解析配置与凭据；请求按冻结维度执行：
 * 可调维度模型（config {@code dimensionAdjustable=true}）携带 {@code dimensions=frozenDimension}，
 * 固定维度模型不带该参数（输出维度以模型为准）。模型不可用/未配置 EMBEDDING 模型时直接报配置错误。
 */
@Slf4j
@Component
public class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {
    public static final int DEFAULT_DIMENSIONS = 1536;
    private static final String TYPE_EMBEDDING = "EMBEDDING";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ModelConfigRetrieve configRetrieve;
    private final CredentialCipher credentialCipher;

    public OpenAiCompatibleEmbeddingClient(ModelConfigRetrieve configRetrieve, CredentialCipher credentialCipher) {
        this.configRetrieve = configRetrieve;
        this.credentialCipher = credentialCipher;
    }

    @Override
    public List<Float> embed(String text, Long embeddingModelId, Integer frozenDimension) {
        var model = resolveModel(embeddingModelId);
        var credentials = AgentModelCredentialConfig.parse(credentialCipher.decrypt(model.credentialsCiphertext()));
        var baseUrl = credentials.baseUrl() == null || credentials.baseUrl().isBlank()
                ? "https://api.openai.com/v1"
                : credentials.baseUrl().replaceAll("/+$", "");

        boolean adjustable = isAdjustable(model.config());
        StringBuilder body = new StringBuilder("{\"model\":\"")
                .append(model.modelKey())
                .append("\",\"input\":\"")
                .append(escape(text))
                .append('"');
        if (adjustable && frozenDimension != null && frozenDimension > 0) {
            body.append(",\"dimensions\":").append(frozenDimension);
        }
        body.append('}');
        try {
            var client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/embeddings"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + (credentials.apiKey() == null ? "" : credentials.apiKey()))
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException(describeFailure(model, frozenDimension, response.statusCode(), response.body()));
            }
            return parseEmbedding(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("embedding request interrupted", exception);
        } catch (Exception exception) {
            // 保留真实原因（状态码/响应体/超时），避免上层只见泛化错误
            String detail = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            throw new IllegalStateException("embedding request failed: " + detail, exception);
        }
    }

    @Override
    public Integer probeDimension(Long embeddingModelId) {
        if (embeddingModelId == null) {
            log.warn("embeddingModelId is null");
            return null;
        }
        try {
            var vector = embed("hello", embeddingModelId, null);
            return vector == null ? null : vector.size();
        } catch (Exception e) {
            log.warn("probe embedding dimension failed for model {}: {}", embeddingModelId, e.getMessage(),e);
            return null;
        }
    }

    /**
     * 解析嵌入模型：优先按 KB 绑定 id；未指定时使用 EMBEDDING 默认模型，
     * 其次使用最早启用模型；没有可用模型时直接报配置错误。
     */
    private ModelRuntimeConfigDTO resolveModel(Long embeddingModelId) {
        ModelRuntimeConfigDTO model = embeddingModelId == null
                ? configRetrieve.findDefaultOrFirstEnabledModel(TYPE_EMBEDDING)
                : configRetrieve.findRuntimeConfigByModelId(embeddingModelId);
        if (model == null) {
            throw new IllegalStateException("未配置可用的 EMBEDDING 模型");
        }
        // KB 绑定错误（非 EMBEDDING 模型）时按未配置处理，回退本地嵌入
        if (!TYPE_EMBEDDING.equalsIgnoreCase(model.modelType())) {
            throw new IllegalStateException("模型 " + model.modelId() + " 不是 EMBEDDING 类型");
        }
        return model;
    }

    private static boolean isAdjustable(ModelExtConfigAttrs config) {
        return config != null && Boolean.TRUE.equals(config.getDimensionAdjustable());
    }

    private List<Float> parseEmbedding(String json) throws Exception {
        var root = OBJECT_MAPPER.readTree(json);
        var data = root.path("data");
        if (!data.isArray() || data.isEmpty()) {
            throw new IllegalStateException("embedding response has no data: " + json);
        }
        var values = new ArrayList<Float>();
        for (var value : data.get(0).path("embedding")) {
            values.add((float) value.asDouble());
        }
        return values;
    }

    /**
     * 把嵌入请求失败转成可操作的信息：上游明确拒绝 {@code dimensions} 参数（400 InvalidParameter
     * param=dimensions）时，说明模型 config 的 {@code dimensionAdjustable/maxDimension} 声明与
     * 实际能力不符——错误信息直接提示修正路径，避免运维只看到裸 400 body。
     */
    private static String describeFailure(ModelRuntimeConfigDTO model, Integer frozenDimension,
                                          int status, String upstreamBody) {
        String base = "embedding request failed with status " + status + ": " + upstreamBody;
        if (frozenDimension == null || frozenDimension <= 0) {
            return base;
        }
        boolean upstreamRejectsDimensions = upstreamBody != null
                && upstreamBody.contains("dimensions")
                && (upstreamBody.contains("InvalidParameter")
                    || upstreamBody.contains("invalid_request_error")
                    || upstreamBody.contains("BadRequest")
                    || upstreamBody.contains("not valid")
                    || upstreamBody.contains("is not valid"));
        if (!upstreamRejectsDimensions) {
            return base;
        }
        String hint = ("embedding request rejected by upstream for model '%s' with HTTP %d: it does not accept "
                + "the dimensions=%d parameter (KB frozen dimension). The model config claims "
                + "dimensionAdjustable=true/maxDimension=%s, which conflicts with the actual upstream capability. "
                + "Fix: edit the model (id=%s) and either set dimensionAdjustable=false with embeddingDimension equal "
                + "to the model's real output dimension (probe via 'test connection'), or adjust the KB frozen "
                + "dimension to an accepted value. Upstream detail: %s")
                .formatted(model.modelKey(), status, frozenDimension,
                        model.config() == null ? "?" : model.config().getMaxDimension(),
                        model.modelId(), summarize(upstreamBody));
        log.warn("embedding dimension mismatch: {}", hint);
        return hint;
    }

    /** 摘要化上游响应，避免把超长原始 body 直接塞进错误信息/error_message。 */
    private static String summarize(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String cleaned = body.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 200 ? cleaned.substring(0, 200) + "…" : cleaned;
    }

    private String escape(String text) {
        return text == null ? "" : text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
