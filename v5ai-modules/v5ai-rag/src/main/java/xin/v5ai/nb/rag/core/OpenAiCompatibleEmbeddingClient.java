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
 * 固定维度模型不带该参数（输出维度以模型为准）。模型不可用/未配置 EMBEDDING 模型时回退
 * 确定性本地 Hash 嵌入（维度 = 冻结维度或默认 1536），保证开发环境可跑。
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
        if (model == null) {
            return HashEmbedding.embed(text, frozenDimension == null ? DEFAULT_DIMENSIONS : frozenDimension);
        }
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
                throw new IllegalStateException("embedding request failed with status " + response.statusCode() + ": " + response.body());
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
            return null;
        }
        try {
            var vector = embed("hello", embeddingModelId, null);
            return vector == null ? null : vector.size();
        } catch (Exception exception) {
            log.warn("probe embedding dimension failed for model {}: {}", embeddingModelId, exception.getMessage());
            return null;
        }
    }

    /**
     * 解析嵌入模型：优先按 KB 绑定 id；未指定时取首个启用的 EMBEDDING 模型；均不可得返回 null（本地回退）。
     */
    private ModelRuntimeConfigDTO resolveModel(Long embeddingModelId) {
        ModelRuntimeConfigDTO model = embeddingModelId == null
                ? configRetrieve.findFirstEmbeddingModel()
                : configRetrieve.findRuntimeConfigByModelId(embeddingModelId);
        if (model == null) {
            return null;
        }
        // KB 绑定错误（非 EMBEDDING 模型）时按未配置处理，回退本地嵌入
        return TYPE_EMBEDDING.equalsIgnoreCase(model.modelType()) ? model : null;
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

    private String escape(String text) {
        return text == null ? "" : text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
