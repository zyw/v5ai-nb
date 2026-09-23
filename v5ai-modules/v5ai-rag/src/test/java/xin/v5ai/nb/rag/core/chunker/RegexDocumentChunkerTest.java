package xin.v5ai.nb.rag.core.chunker;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.rag.core.ChunkMode;
import xin.v5ai.nb.rag.core.ChunkingOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link RegexDocumentChunker} 单测：按 Java 正则一级切分后再按最大长度切分。
 *
 * @author ZYW
 * @since 2026-09-08
 */
class RegexDocumentChunkerTest {

    private final RegexDocumentChunker chunker =
            new RegexDocumentChunker(new ChunkPipeline(new FixedLengthSplitter(), new ChunkMerger()));

    @Test
    void splitsByJavaPattern() {
        var options = new ChunkingOptions(ChunkMode.REGEX, 1000, 100, null, "\\n{2,}", false);

        var chunks = chunker.chunk("one\n\n\ntwo", options);

        assertThat(chunks).containsExactly("one", "two");
    }

    @Test
    void rejectsInvalidPattern() {
        var options = new ChunkingOptions(ChunkMode.REGEX, 1000, 100, null, "[", false);

        assertThatThrownBy(() -> chunker.chunk("text", options))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("切片正则不合法");
    }
}
