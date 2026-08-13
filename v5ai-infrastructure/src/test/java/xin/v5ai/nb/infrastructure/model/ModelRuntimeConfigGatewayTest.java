package xin.v5ai.nb.infrastructure.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelRuntimeConfigGatewayTest {
    @Test
    void runtimeConfigContainsModelAndProviderFields() {
        var config = new ModelRuntimeConfig(
                7L,
                "gpt-4o",
                "CHAT",
                3L,
                "openai",
                "OPENAI_COMPATIBLE",
                "ciphertext"
        );

        assertThat(config.modelId()).isEqualTo(7L);
        assertThat(config.modelKey()).isEqualTo("gpt-4o");
        assertThat(config.modelType()).isEqualTo("CHAT");
        assertThat(config.providerId()).isEqualTo(3L);
        assertThat(config.providerKey()).isEqualTo("openai");
        assertThat(config.providerType()).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(config.credentialsCiphertext()).isEqualTo("ciphertext");
    }
}
