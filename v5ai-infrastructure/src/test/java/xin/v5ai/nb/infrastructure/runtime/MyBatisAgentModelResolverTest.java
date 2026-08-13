package xin.v5ai.nb.infrastructure.runtime;

import io.agentscope.core.model.Model;
import org.junit.jupiter.api.Test;
import xin.v5ai.nb.infrastructure.model.ModelRuntimeConfig;
import xin.v5ai.nb.infrastructure.model.ModelRuntimeConfigGateway;
import xin.v5ai.nb.model.CredentialCipher;

import static org.assertj.core.api.Assertions.assertThat;

class MyBatisAgentModelResolverTest {
    @Test
    void resolvesModelByDecryptingRuntimeConfigCredentials() {
        var gateway = new RecordingModelRuntimeConfigGateway();
        var factory = new RecordingAgentScopeModelFactory();
        CredentialCipher cipher = new CredentialCipher() {
            @Override
            public String encrypt(String plaintext) {
                return "unused";
            }

            @Override
            public String decrypt(String ciphertext) {
                return "plaintext:" + ciphertext;
            }
        };
        var resolver = new MyBatisAgentModelResolver(gateway, cipher, factory);

        Model model = resolver.resolve(7L);

        assertThat(model.getModelName()).isEqualTo("factory-model");
        assertThat(factory.receivedConfig.modelId()).isEqualTo(7L);
        assertThat(factory.receivedPlaintext).isEqualTo("plaintext:ciphertext");
    }

    private static final class RecordingModelRuntimeConfigGateway implements ModelRuntimeConfigGateway {
        @Override
        public ModelRuntimeConfig findRuntimeConfigByModelId(Long modelId) {
            return new ModelRuntimeConfig(modelId, "gpt-4o", "CHAT", 3L, "openai", "OPENAI", "ciphertext");
        }
    }

    private static final class RecordingAgentScopeModelFactory extends AgentScopeModelFactory {
        private ModelRuntimeConfig receivedConfig;
        private String receivedPlaintext;

        @Override
        public Model create(ModelRuntimeConfig config, String plaintextCredentials) {
            this.receivedConfig = config;
            this.receivedPlaintext = plaintextCredentials;
            return new FixedModel("factory-model");
        }
    }

    private record FixedModel(String getModelName) implements Model {
        @Override
        public reactor.core.publisher.Flux<io.agentscope.core.model.ChatResponse> stream(
                java.util.List<io.agentscope.core.message.Msg> messages,
                java.util.List<io.agentscope.core.model.ToolSchema> tools,
                io.agentscope.core.model.GenerateOptions options) {
            return reactor.core.publisher.Flux.empty();
        }
    }
}
