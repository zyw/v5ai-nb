package xin.v5ai.nb.common.agentscope.core.factory;

import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.extensions.model.anthropic.AnthropicChatModel;
import io.agentscope.extensions.model.gemini.GeminiChatModel;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.common.agentscope.core.domain.dto.ModelExtConfigAttrs;
import xin.v5ai.nb.common.agentscope.core.domain.dto.ModelRuntimeConfigDTO;
import xin.v5ai.nb.common.agentscope.core.factory.config.AgentModelCredentialConfig;

@Component
public class AgentScopeModelFactory {
    public Model create(ModelRuntimeConfigDTO runtimeConfig, String plaintextCredentials) {
        if (!"CHAT".equals(runtimeConfig.modelType())) {
            throw new IllegalArgumentException("only CHAT models can be used by agent runtime");
        }
        var credentials = AgentModelCredentialConfig.parse(plaintextCredentials);
        var generateOptions = generateOptions(runtimeConfig.config());
        return switch (runtimeConfig.providerKey()) {
            case "anthropic" -> anthropicModel(runtimeConfig, credentials, generateOptions);
            case "gemini" -> geminiModel(runtimeConfig, credentials, generateOptions);
            // dashscope 走 OpenAI 兼容适配器：平台收集的 baseUrl 是 .../compatible-mode/v1，
            // 原生适配器（DashScopeHttpClient）会把它与 /api/v1/services/aigc/... 直接字符串拼接，
            // 该路径在 compatible-mode 前缀下不存在，必然 404。
            case "dashscope" -> openAiModel(runtimeConfig, credentials, generateOptions);
            default -> {
                if ("openai-compatible".equals(runtimeConfig.adapterKey())) {
                    yield openAiModel(runtimeConfig, credentials, generateOptions);
                }
                throw new IllegalArgumentException("unsupported adapter key: " + runtimeConfig.adapterKey());
            }
        };
    }

    private Model geminiModel(ModelRuntimeConfigDTO config, AgentModelCredentialConfig credentials, GenerateOptions options) {
        var builder = GeminiChatModel.builder()
                .apiKey(credentials.apiKey())
                .modelName(config.modelKey())
                .streamEnabled(true)
                .defaultOptions(options);
        if (credentials.baseUrl() != null && !credentials.baseUrl().isBlank()) {
            builder.baseUrl(credentials.baseUrl());
        }
        return builder.build();
    }

    private Model anthropicModel(ModelRuntimeConfigDTO config, AgentModelCredentialConfig credentials, GenerateOptions options) {
        var builder = AnthropicChatModel.builder()
                .apiKey(credentials.apiKey())
                .modelName(config.modelKey())
                .stream(true)
                .defaultOptions(options);
        if (credentials.baseUrl() != null && !credentials.baseUrl().isBlank()) {
            builder.baseUrl(credentials.baseUrl());
        }
        return builder.build();
    }

    private Model openAiModel(ModelRuntimeConfigDTO config, AgentModelCredentialConfig credentials, GenerateOptions options) {
        var builder = OpenAIChatModel.builder()
                .apiKey(credentials.apiKey())
                .modelName(config.modelKey())
                .stream(true)
                .generateOptions(options);
        if (credentials.baseUrl() != null && !credentials.baseUrl().isBlank()) {
            builder.baseUrl(credentials.baseUrl());
        }
        return builder.build();
    }

    /**
     * 由模型扩展参数构建生成选项；{@code config} 为 null 或字段全空时返回空 GenerateOptions。
     * <p>
     * 仅映射与 GenerateOptions 语义 1:1 的 CHAT 参数（config 为唯一权威来源）。
     * stream 映射到生成参数层的 stream；模型级流式传输开关在 builder 上保持开启。
     * responseFormat（GenerateOptions 侧为 jsonSchema 构造器，非字符串）、stopSequences、
     * extraBody、timeoutMs/maxRetries 等留待后续接入。
     */
    private GenerateOptions generateOptions(ModelExtConfigAttrs config) {
        var builder = GenerateOptions.builder();
        if (config != null) {
            if (config.getTemperature() != null) {
                builder.temperature(config.getTemperature());
            }
            if (config.getTopP() != null) {
                builder.topP(config.getTopP());
            }
            if (config.getTopK() != null) {
                builder.topK(config.getTopK());
            }
            if (config.getMaxTokens() != null) {
                builder.maxTokens(config.getMaxTokens());
            }
            if (config.getFrequencyPenalty() != null) {
                builder.frequencyPenalty(config.getFrequencyPenalty());
            }
            if (config.getPresencePenalty() != null) {
                builder.presencePenalty(config.getPresencePenalty());
            }
            if (config.getSeed() != null) {
                builder.seed(config.getSeed());
            }
            if (config.getStream() != null) {
                builder.stream(config.getStream());
            }
        }
        return builder.build();
    }
}
