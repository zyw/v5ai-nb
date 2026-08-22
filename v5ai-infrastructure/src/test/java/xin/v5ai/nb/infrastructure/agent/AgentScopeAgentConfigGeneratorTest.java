package xin.v5ai.nb.infrastructure.agent;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import xin.v5ai.nb.common.encrypt.core.CredentialCipher;
import xin.v5ai.nb.model.mapper.ModelRuntimeConfigGateway;
import xin.v5ai.nb.model.runtime.AgentScopeModelFactory;
import xin.v5ai.nb.model.runtime.config.ModelRuntimeConfig;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentScopeAgentConfigGeneratorTest {

    private static final ModelRuntimeConfig RUNTIME_CONFIG = new ModelRuntimeConfig(
            7L, "gpt-test", "CHAT", 1L, "openai", "OPENAI", "encrypted");

    @Test
    void parsesStrictJsonFromModelResponse() {
        var generator = generatorWith(rawResponse("""
                {"name":"智能客服","description":"7x24 在线客服","greeting":"您好，有什么可以帮您？",\
                "presetQuestions":["怎么退款？","如何联系人工？"],"systemPrompt":"You are a customer service agent."}""".trim()));

        var result = generator.generate(7L, "帮我设计一个智能客服");

        assertThat(result.name()).isEqualTo("智能客服");
        assertThat(result.description()).isEqualTo("7x24 在线客服");
        assertThat(result.greeting()).isEqualTo("您好，有什么可以帮您？");
        assertThat(result.presetQuestions()).containsExactly("怎么退款？", "如何联系人工？");
        assertThat(result.systemPrompt()).isEqualTo("You are a customer service agent.");
    }

    @Test
    void stripsMarkdownCodeFence() {
        var generator = generatorWith(rawResponse("""
                ```json
                {"name":"demo","presetQuestions":["q1"]}
                ```""".trim()));

        var result = generator.generate(7L, "demo agent");

        assertThat(result.name()).isEqualTo("demo");
        assertThat(result.presetQuestions()).containsExactly("q1");
        assertThat(result.description()).isEmpty();
    }

    @Test
    void rejectsBlankDescription() {
        var generator = generatorWith(rawResponse("{}"));

        assertThatThrownBy(() -> generator.generate(7L, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("description is required");
    }

    @Test
    void rejectsUnparseableOutput() {
        var generator = generatorWith(rawResponse("sorry, I cannot help"));

        assertThatThrownBy(() -> generator.generate(7L, "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无法解析");
    }

    private static AgentScopeAgentConfigGenerator generatorWith(Model model) {
        var gateway = new ModelRuntimeConfigGateway() {
            @Override
            public ModelRuntimeConfig findRuntimeConfigByModelId(Long modelId) {
                return RUNTIME_CONFIG;
            }

            @Override
            public ModelRuntimeConfig findFirstEmbeddingModel() {
                return null;
            }
        };
        var cipher = new CredentialCipher() {
            @Override
            public String encrypt(String plaintext) {
                return plaintext;
            }

            @Override
            public String decrypt(String ciphertext) {
                return "{\"apiKey\":\"test\",\"baseUrl\":\"https://example.com\",\"modelKey\":\"gpt-test\"}";
            }
        };
        var modelFactory = new AgentScopeModelFactory() {
            @Override
            public Model create(ModelRuntimeConfig config, String plaintextCredentials) {
                return model;
            }
        };
        return new AgentScopeAgentConfigGenerator(gateway, cipher, modelFactory, Duration.ofSeconds(5));
    }

    private static Model rawResponse(String text) {
        var captured = new AtomicReference<List<Msg>>();
        return new Model() {
            @Override
            public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
                captured.set(messages);
                return Flux.just(ChatResponse.builder()
                        .content(List.of(TextBlock.builder().text(text).build()))
                        .metadata(Map.of())
                        .finishReason("stop")
                        .build());
            }

            @Override
            public String getModelName() {
                return "test-model";
            }
        };
    }
}
