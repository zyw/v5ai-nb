package xin.v5ai.nb.rag.core.chunker;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.rag.core.ChunkMode;
import xin.v5ai.nb.rag.core.ChunkingOptions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SmartDocumentChunker} 单测：未接入对话模型时回退为按段落切分。
 *
 * @author ZYW
 * @since 2026-09-08
 */
class SmartDocumentChunkerTest {

    private final SmartDocumentChunker chunker =
            new SmartDocumentChunker(new ChunkPipeline(new FixedLengthSplitter(), new ChunkMerger()));

    @Test
    void fallsBackToParagraphSplit() {
        var options = new ChunkingOptions(ChunkMode.SMART, 1000, 100, null, null, false);

        var chunks = chunker.chunk("para one\n\npara two\n\npara three", options);

        assertThat(chunks).containsExactly("para one", "para two", "para three");
    }
}
