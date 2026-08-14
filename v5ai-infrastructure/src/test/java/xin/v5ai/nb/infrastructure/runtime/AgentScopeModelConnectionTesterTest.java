package xin.v5ai.nb.infrastructure.runtime;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import xin.v5ai.nb.infrastructure.model.ModelRuntimeConfig;
import xin.v5ai.nb.infrastructure.model.ModelRuntimeConfigGateway;
import xin.v5ai.nb.model.ConnectionTestResult;
import xin.v5ai.nb.model.CredentialCipher;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentScopeModelConnectionTesterTest {

    @Test
    void returnsOkWhenModelResponds() {
        var tester = tester(new RespondingModel("pong"));

        ConnectionTestResult result = tester.test(2L);

        assertThat(result.ok()).isTrue();
    }

    @Test
    void surfacesTheRealErrorMessageInsteadOfGenericFailure() {
        var tester = tester(new ErroringModel("401 Unauthorized: invalid api key"));

        ConnectionTestResult result = tester.test(2L);

        assertThat(result.ok()).isFalse();
        assertThat(result.message()).contains("401 Unauthorized");
    }

    @Test
    void reportsEmptyStreamWithoutResponse() {
        var tester = tester(new EmptyModel());

        ConnectionTestResult result = tester.test(2L);

        assertThat(result.ok()).isFalse();
        assertThat(result.message()).contains("no response");
    }

    private AgentScopeModelConnectionTester tester(Model model) {
        CredentialCipher cipher = new CredentialCipher() {
            @Override
            public String encrypt(String plaintext) {
                return "ciphertext";
            }

            @Override
            public String decrypt(String ciphertext) {
                return "plaintext-credentials";
            }
        };
        return new AgentScopeModelConnectionTester(
                new FixedGateway(),
                cipher,
                new FixedFactory(model));
    }

    private static final class FixedGateway implements ModelRuntimeConfigGateway {
        @Override
        public ModelRuntimeConfig findRuntimeConfigByModelId(Long modelId) {
            return new ModelRuntimeConfig(modelId, "gpt-4o-mini", "CHAT", 1L, "openai", "OPENAI",
                    "ciphertext");
        }

        @Override
        public ModelRuntimeConfig findFirstEmbeddingModel() {
            return null;
        }
    }

    private static final class FixedFactory extends AgentScopeModelFactory {
        private final Model model;

        private FixedFactory(Model model) {
            this.model = model;
        }

        @Override
        public Model create(ModelRuntimeConfig config, String plaintextCredentials) {
            return model;
        }
    }

    private static final class RespondingModel implements Model {
        private final String text;

        private RespondingModel(String text) {
            this.text = text;
        }

        @Override
        public Flux<ChatResponse> stream(List<io.agentscope.core.message.Msg> messages,
                                         List<ToolSchema> tools,
                                         GenerateOptions options) {
            return Flux.just(ChatResponse.builder()
                    .content(List.of(TextBlock.builder().text(text).build()))
                    .metadata(Map.of())
                    .finishReason("stop")
                    .build());
        }

        @Override
        public String getModelName() {
            return "test";
        }
    }

    private static final class ErroringModel implements Model {
        private final String error;

        private ErroringModel(String error) {
            this.error = error;
        }

        @Override
        public Flux<ChatResponse> stream(List<io.agentscope.core.message.Msg> messages,
                                         List<ToolSchema> tools,
                                         GenerateOptions options) {
            return Flux.error(new RuntimeException(error));
        }

        @Override
        public String getModelName() {
            return "test";
        }
    }

    private static final class EmptyModel implements Model {
        @Override
        public Flux<ChatResponse> stream(List<io.agentscope.core.message.Msg> messages,
                                         List<ToolSchema> tools,
                                         GenerateOptions options) {
            return Flux.empty();
        }

        @Override
        public String getModelName() {
            return "test";
        }
    }
}
