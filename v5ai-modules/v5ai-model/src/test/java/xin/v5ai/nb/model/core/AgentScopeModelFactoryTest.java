package xin.v5ai.nb.model.core;

import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.agentscope.core.domain.dto.ModelRuntimeConfigDTO;
import xin.v5ai.nb.common.agentscope.core.factory.AgentScopeModelFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentScopeModelFactoryTest {
    @Test
    void createsOpenAiCompatibleChatModel() {
        var factory = new AgentScopeModelFactory();

        var model = factory.create(config("CHAT", "my-provider", "openai-compatible"), """
                {"apiKey":"sk-test","baseUrl":"https://api.example.com/v1"}
                """);

        assertThat(model).isInstanceOf(OpenAIChatModel.class);
        assertThat(model.getModelName()).isEqualTo("gpt-4o");
    }

    @Test
    void createsDashScopeChatModel() {
        var factory = new AgentScopeModelFactory();

        var model = factory.create(config("CHAT", "dashscope", "openai-compatible"), """
                {"apiKey":"sk-test","baseUrl":"https://dashscope.aliyuncs.com/compatible-mode/v1"}
                """);

        assertThat(model).isInstanceOf(DashScopeChatModel.class);
        assertThat(model.getModelName()).isEqualTo("gpt-4o");
    }

    @Test
    void rejectsNonChatModelForAgentRuntime() {
        var factory = new AgentScopeModelFactory();

        assertThatThrownBy(() -> factory.create(config("EMBEDDING", "my-provider", "openai-compatible"), "{\"apiKey\":\"sk\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("only CHAT models can be used by agent runtime");
    }

    @Test
    void rejectsUnsupportedAdapterKey() {
        var factory = new AgentScopeModelFactory();

        assertThatThrownBy(() -> factory.create(config("CHAT", "my-provider", "http"), "{\"apiKey\":\"sk\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unsupported adapter key: http");
    }

    private static ModelRuntimeConfigDTO config(String modelType, String providerKey, String adapterKey) {
        return new ModelRuntimeConfigDTO(7L, "gpt-4o", modelType, 3L, providerKey, adapterKey, "ciphertext", null);
    }
}
