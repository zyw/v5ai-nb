package xin.v5ai.nb.rag.core.chunker;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.rag.core.ChunkMode;
import xin.v5ai.nb.rag.core.ChunkingOptions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DelimiterDocumentChunker} 单测：按分隔符一级切分后再按最大长度切分。
 *
 * @author ZYW
 * @since 2026-09-08
 */
class DelimiterDocumentChunkerTest {

    private final DelimiterDocumentChunker chunker =
            new DelimiterDocumentChunker(new ChunkPipeline(new FixedLengthSplitter(), new ChunkMerger()));

    @Test
    void splitsByDelimiterThenLength() {
        var options = new ChunkingOptions(ChunkMode.DELIMITER, 1000, 100, "\n\n", null, false);

        var chunks = chunker.chunk("first\n\nsecond paragraph\n\nthird", options);

        assertThat(chunks).containsExactly("first", "second paragraph", "third");
    }

    @Test
    void fallsBackToDefaultDelimiterWhenBlank() {
        var options = new ChunkingOptions(ChunkMode.DELIMITER, 1000, 100, null, null, false);

        var chunks = chunker.chunk("a\n\nb", options);

        assertThat(chunks).containsExactly("a", "b");
    }

    @Test
    void splitsByAnyOfJsonDelimiters() {
        var json = "[\"\\n\\n\",\"。\"]";
        var options = new ChunkingOptions(ChunkMode.DELIMITER, 1000, 100, json, null, false);

        var chunks = chunker.chunk("one\n\ntwo。three\n\nfour", options);

        assertThat(chunks).containsExactly("one", "two", "three", "four");
    }

    @Test
    void fallsBackToDefaultWhenInvalidJson() {
        var options = new ChunkingOptions(ChunkMode.DELIMITER, 1000, 100, "not-a-json", null, false);

        var chunks = chunker.chunk("a\n\nb", options);

        assertThat(chunks).containsExactly("a", "b");
    }
}
