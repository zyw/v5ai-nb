package xin.v5ai.nb.rag.core.chunker;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.rag.core.ChunkMode;
import xin.v5ai.nb.rag.core.ChunkingOptions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LengthDocumentChunker} 单测：默认/自定义长度切分、段落粗分后再按最大长度切分。
 *
 * @author ZYW
 * @since 2026-09-08
 */
class LengthDocumentChunkerTest {

    private final LengthDocumentChunker chunker =
            new LengthDocumentChunker(new ChunkPipeline(new FixedLengthSplitter(), new ChunkMerger()));

    @Test
    void chunkWithDefaultsUsesFixedSize() {
        var chunks = chunker.chunk("a".repeat(2000));

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0)).hasSize(800);
        assertThat(chunks.get(1)).hasSize(800);
        assertThat(chunks.get(2)).hasSize(600);
    }

    @Test
    void chunkBlankTextReturnsEmpty() {
        assertThat(chunker.chunk(null)).isEmpty();
        assertThat(chunker.chunk("   ")).isEmpty();
    }

    @Test
    void chunkWithCustomMaxAndOverlap() {
        var options = new ChunkingOptions(ChunkMode.LENGTH, 10, 2, null, null, false);

        var chunks = chunker.chunk("abcdefghijklmnopqrst", options);

        assertThat(chunks.get(0)).isEqualTo("abcdefghij");
        assertThat(chunks.get(1)).isEqualTo("ijklmnopqr");
    }

    @Test
    void chunkSplitsByParagraphsFirstThenByMaxLength() {
        // 先按空行粗分段落，短段落保持完整，不跨段落边界滑动切分
        var options = new ChunkingOptions(ChunkMode.LENGTH, 1000, 100, null, null, false);

        var chunks = chunker.chunk("para one\n\npara two\n\npara three", options);

        assertThat(chunks).containsExactly("para one", "para two", "para three");
    }

    @Test
    void chunkSplitsLongParagraphBeyondMax() {
        // 超长段落再按最大长度滑动切分（带重叠），其余段落保持独立
        var options = new ChunkingOptions(ChunkMode.LENGTH, 10, 2, null, null, false);

        var chunks = chunker.chunk("aaaa bbbb cccc\n\ndddd", options);

        assertThat(chunks).containsExactly("aaaa bbbb ", "b cccc", "dddd");
    }
}
