package xin.v5ai.nb.model.mapper;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.agentscope.core.domain.dto.ModelExtConfigAttrs;
import xin.v5ai.nb.common.agentscope.core.domain.dto.ModelRuntimeConfigDTO;

import static org.assertj.core.api.Assertions.assertThat;

class ModelRuntimeConfigGatewayTest {
    @Test
    void runtimeConfigContainsModelAndProviderFields() {
        var config = new ModelRuntimeConfigDTO(
                7L,
                "gpt-4o",
                "CHAT",
                3L,
                "openai",
                "openai-compatible",
                "ciphertext",
                ModelExtConfigAttrs.fromJson("""
                        {"temperature":0.7,"maxTokens":4096}
                        """)
        );

        assertThat(config.modelId()).isEqualTo(7L);
        assertThat(config.modelKey()).isEqualTo("gpt-4o");
        assertThat(config.modelType()).isEqualTo("CHAT");
        assertThat(config.providerId()).isEqualTo(3L);
        assertThat(config.providerKey()).isEqualTo("openai");
        assertThat(config.adapterKey()).isEqualTo("openai-compatible");
        assertThat(config.credentialsCiphertext()).isEqualTo("ciphertext");
        assertThat(config.config()).isNotNull();
        assertThat(config.config().getTemperature()).isEqualTo(0.7);
        assertThat(config.config().getMaxTokens()).isEqualTo(4096);
    }
}
