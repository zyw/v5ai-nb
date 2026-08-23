package xin.v5ai.nb.model.core;

import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.agentscope.core.factory.AgentScopeModelFactory;
import xin.v5ai.nb.model.core.config.ModelRuntimeConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentScopeModelFactoryTest {
    @Test
    void createsOpenAiCompatibleChatModel() {
        var factory = new AgentScopeModelFactory();

        var model = factory.create(config("CHAT", "OPENAI_COMPATIBLE"), """
                {"apiKey":"sk-test","baseUrl":"https://api.example.com/v1","temperature":0.3,"maxTokens":1024}
                """);

        assertThat(model).isInstanceOf(OpenAIChatModel.class);
        assertThat(model.getModelName()).isEqualTo("gpt-4o");
    }

    @Test
    void createsDashScopeChatModel() {
        var factory = new AgentScopeModelFactory();

        var model = factory.create(config("CHAT", "DASHSCOPE"), """
                {"apiKey":"sk-test","baseUrl":"https://dashscope.aliyuncs.com/compatible-mode/v1"}
                """);

        assertThat(model).isInstanceOf(DashScopeChatModel.class);
        assertThat(model.getModelName()).isEqualTo("gpt-4o");
    }

    @Test
    void rejectsNonChatModelForAgentRuntime() {
        var factory = new AgentScopeModelFactory();

        assertThatThrownBy(() -> factory.create(config("EMBEDDING", "OPENAI"), "{\"apiKey\":\"sk\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("only CHAT models can be used by agent runtime");
    }

    @Test
    void rejectsUnsupportedProviderType() {
        var factory = new AgentScopeModelFactory();

        assertThatThrownBy(() -> factory.create(config("CHAT", "UNKNOWN"), "{\"apiKey\":\"sk\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unsupported provider type: UNKNOWN");
    }

    private static ModelRuntimeConfig config(String modelType, String providerType) {
        return new ModelRuntimeConfig(7L, "gpt-4o", modelType, 3L, "provider", providerType, "ciphertext");
    }
}
