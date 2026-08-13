package xin.v5ai.nb.infrastructure.runtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentModelCredentialConfigTest {
    @Test
    void parsesCompleteCredentialJson() {
        var config = AgentModelCredentialConfig.parse("""
                {
                  "apiKey": "sk-test",
                  "baseUrl": "https://api.example.com/v1",
                  "temperature": 0.7,
                  "maxTokens": 2048
                }
                """);

        assertThat(config.apiKey()).isEqualTo("sk-test");
        assertThat(config.baseUrl()).isEqualTo("https://api.example.com/v1");
        assertThat(config.temperature()).isEqualTo(0.7);
        assertThat(config.maxTokens()).isEqualTo(2048);
    }

    @Test
    void optionalFieldsMayBeMissing() {
        var config = AgentModelCredentialConfig.parse("""
                {"apiKey":"sk-test"}
                """);

        assertThat(config.apiKey()).isEqualTo("sk-test");
        assertThat(config.baseUrl()).isNull();
        assertThat(config.temperature()).isNull();
        assertThat(config.maxTokens()).isNull();
    }
}
