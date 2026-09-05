package xin.v5ai.nb.common.agentscope.core.factory.config;

import tools.jackson.databind.ObjectMapper;

/**
 * 模型凭据配置（AES-GCM 密文的明文形态）。
 * <p>
 * 仅承载连接凭据（apiKey/baseUrl）；temperature/maxTokens 等生成参数已统一归入
 * 模型扩展参数（v5ai_model.config，{@code ModelExtConfigAttrs}），不再从凭据读取。
 */
public record AgentModelCredentialConfig(String apiKey, String baseUrl) {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static AgentModelCredentialConfig parse(String json) {
        try {
            var node = OBJECT_MAPPER.readTree(json);
            return new AgentModelCredentialConfig(
                    textOrNull(node, "apiKey"),
                    textOrNull(node, "baseUrl")
            );
        } catch (Exception exception) {
            throw new IllegalArgumentException("invalid model credential json", exception);
        }
    }

    private static String textOrNull(tools.jackson.databind.JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
