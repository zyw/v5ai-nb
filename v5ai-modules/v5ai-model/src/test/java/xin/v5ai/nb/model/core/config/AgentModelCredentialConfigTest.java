package xin.v5ai.nb.model.core.config;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.agentscope.core.factory.config.AgentModelCredentialConfig;

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
    }

    @Test
    void optionalFieldsMayBeMissing() {
        var config = AgentModelCredentialConfig.parse("""
                {"apiKey":"sk-test"}
                """);

        assertThat(config.apiKey()).isEqualTo("sk-test");
        assertThat(config.baseUrl()).isNull();
    }
}
