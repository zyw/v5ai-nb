package xin.v5ai.nb.model.mapper;

import org.junit.jupiter.api.Test;
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
                "ciphertext"
        );

        assertThat(config.modelId()).isEqualTo(7L);
        assertThat(config.modelKey()).isEqualTo("gpt-4o");
        assertThat(config.modelType()).isEqualTo("CHAT");
        assertThat(config.providerId()).isEqualTo(3L);
        assertThat(config.providerKey()).isEqualTo("openai");
        assertThat(config.adapterKey()).isEqualTo("openai-compatible");
        assertThat(config.credentialsCiphertext()).isEqualTo("ciphertext");
    }
}
