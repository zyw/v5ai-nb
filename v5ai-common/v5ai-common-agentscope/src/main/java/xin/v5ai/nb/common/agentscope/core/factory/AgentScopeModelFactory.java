package xin.v5ai.nb.common.agentscope.core.factory;

import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.common.agentscope.core.domain.dto.ModelRuntimeConfigDTO;
import xin.v5ai.nb.common.agentscope.core.factory.config.AgentModelCredentialConfig;

@Component
public class AgentScopeModelFactory {
    public Model create(ModelRuntimeConfigDTO config, String plaintextCredentials) {
        if (!"CHAT".equals(config.modelType())) {
            throw new IllegalArgumentException("only CHAT models can be used by agent runtime");
        }
        var credentials = AgentModelCredentialConfig.parse(plaintextCredentials);
        return switch (config.providerType()) {
            case "OPENAI", "OPENAI_COMPATIBLE" -> openAiModel(config, credentials);
            case "DASHSCOPE" -> dashScopeModel(config, credentials);
            default -> throw new IllegalArgumentException("unsupported provider type: " + config.providerType());
        };
    }

    private Model openAiModel(ModelRuntimeConfigDTO config, AgentModelCredentialConfig credentials) {
        var builder = OpenAIChatModel.builder()
                .apiKey(credentials.apiKey())
                .modelName(config.modelKey())
                .stream(true)
                .generateOptions(generateOptions(credentials));
        if (credentials.baseUrl() != null && !credentials.baseUrl().isBlank()) {
            builder.baseUrl(credentials.baseUrl());
        }
        return builder.build();
    }

    private Model dashScopeModel(ModelRuntimeConfigDTO config, AgentModelCredentialConfig credentials) {
        var builder = DashScopeChatModel.builder()
                .apiKey(credentials.apiKey())
                .modelName(config.modelKey())
                .stream(true)
                .defaultOptions(generateOptions(credentials));
        if (credentials.baseUrl() != null && !credentials.baseUrl().isBlank()) {
            builder.baseUrl(credentials.baseUrl());
        }
        return builder.build();
    }

    private GenerateOptions generateOptions(AgentModelCredentialConfig credentials) {
        var builder = GenerateOptions.builder();
        if (credentials.temperature() != null) {
            builder.temperature(credentials.temperature());
        }
        if (credentials.maxTokens() != null) {
            builder.maxTokens(credentials.maxTokens());
        }
        return builder.build();
    }
}
