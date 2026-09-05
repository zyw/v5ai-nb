package xin.v5ai.nb.common.agentscope.core.domain.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelExtConfigAttrsTest {

    @Test
    void parsesConfigJsonFromUiPayload() {
        var config = ModelExtConfigAttrs.fromJson("""
                {"temperature":0.7,"topP":1,"topK":1,"maxTokens":4096,"frequencyPenalty":0}
                """);

        assertThat(config).isNotNull();
        assertThat(config.getTemperature()).isEqualTo(0.7);
        assertThat(config.getTopP()).isEqualTo(1.0);
        assertThat(config.getTopK()).isEqualTo(1);
        assertThat(config.getMaxTokens()).isEqualTo(4096);
        assertThat(config.getFrequencyPenalty()).isZero();
    }

    @Test
    void blankJsonYieldsNull() {
        assertThat(ModelExtConfigAttrs.fromJson(null)).isNull();
        assertThat(ModelExtConfigAttrs.fromJson("  ")).isNull();
    }

    @Test
    void toJsonRoundTrips() {
        var original = ModelExtConfigAttrs.fromJson(
                "{\"temperature\":0.3,\"maxTokens\":2048,\"seed\":42}");
        var copy = ModelExtConfigAttrs.fromJson(ModelExtConfigAttrs.toJson(original));

        assertThat(copy.getTemperature()).isEqualTo(0.3);
        assertThat(copy.getMaxTokens()).isEqualTo(2048);
        assertThat(copy.getSeed()).isEqualTo(42L);
    }

    @Test
    void invalidJsonIsRejected() {
        assertThatThrownBy(() -> ModelExtConfigAttrs.fromJson("{not json"))
                .isInstanceOf(Exception.class);
    }
}
