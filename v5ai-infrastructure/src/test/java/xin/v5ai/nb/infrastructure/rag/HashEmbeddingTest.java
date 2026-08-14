package xin.v5ai.nb.infrastructure.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HashEmbeddingTest {
    @Test
    void producesDeterministicFixedSizeVector() {
        var first = HashEmbedding.embed("hello world", 1536);
        var second = HashEmbedding.embed("hello world", 1536);

        assertThat(first).hasSize(1536);
        assertThat(first).isEqualTo(second);
    }

    @Test
    void differentTextsProduceDifferentVectors() {
        var first = HashEmbedding.embed("hello", 1536);
        var second = HashEmbedding.embed("world", 1536);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void vectorValuesStayInUnitRange() {
        var vector = HashEmbedding.embed("v5ai", 1536);

        assertThat(vector).allMatch(value -> value >= -1.0f && value <= 1.0f);
    }
}
