package xin.v5ai.nb.infrastructure.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FixedSizeDocumentChunkerTest {
    @Test
    void shortTextStaysAsSingleChunk() {
        var chunker = new FixedSizeDocumentChunker();

        assertThat(chunker.chunk("hello")).containsExactly("hello");
    }

    @Test
    void longTextIsSplitWithOverlap() {
        var chunker = new FixedSizeDocumentChunker();
        var text = "a".repeat(500) + " b " + "c".repeat(500);

        var chunks = chunker.chunk(text, 100, 10);

        assertThat(chunks).hasSizeGreaterThan(1);
        for (String chunk : chunks) {
            assertThat(chunk.length()).isLessThanOrEqualTo(100);
        }
    }

    @Test
    void blankTextProducesNoChunks() {
        var chunker = new FixedSizeDocumentChunker();

        assertThat(chunker.chunk("   ")).isEmpty();
    }
}
