package xin.v5ai.nb.rag.core;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.rag.core.chunker.FixedSizeDocumentChunker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link FixedSizeDocumentChunker} 单测：默认长度切片与按知识库配置的分隔符/正则/智能切片。
 *
 * @author ZYW
 * @since 2026-08-30
 */
class FixedSizeDocumentChunkerTest {

    private final FixedSizeDocumentChunker chunker = new FixedSizeDocumentChunker();

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
        var options = new ChunkingOptions("length", 10, 2, null, null, false);

        var chunks = chunker.chunk("abcdefghijklmnopqrst", options);

        assertThat(chunks.get(0)).isEqualTo("abcdefghij");
        assertThat(chunks.get(1)).isEqualTo("ijklmnopqr");
    }

    @Test
    void delimiterModeSplitsByDelimiterThenLength() {
        var options = new ChunkingOptions("delimiter", 1000, 100, "\n\n", null, false);

        var chunks = chunker.chunk("first\n\nsecond paragraph\n\nthird", options);

        assertThat(chunks).containsExactly("first", "second paragraph", "third");
    }

    @Test
    void delimiterModeFallsBackToDefaultDelimiterWhenBlank() {
        var options = new ChunkingOptions("delimiter", 1000, 100, null, null, false);

        var chunks = chunker.chunk("a\n\nb", options);

        assertThat(chunks).containsExactly("a", "b");
    }

    @Test
    void delimiterModeSplitsByAnyOfJsonDelimiters() {
        var json = "[\"\\n\\n\",\"。\"]";
        var options = new ChunkingOptions("delimiter", 1000, 100, json, null, false);

        var chunks = chunker.chunk("one\n\ntwo。three\n\nfour", options);

        assertThat(chunks).containsExactly("one", "two", "three", "four");
    }

    @Test
    void delimiterModeFallsBackToDefaultWhenInvalidJson() {
        var options = new ChunkingOptions("delimiter", 1000, 100, "not-a-json", null, false);

        var chunks = chunker.chunk("a\n\nb", options);

        assertThat(chunks).containsExactly("a", "b");
    }

    @Test
    void regexModeSplitsByJavaPattern() {
        var options = new ChunkingOptions("regex", 1000, 100, null, "\\n{2,}", false);

        var chunks = chunker.chunk("one\n\n\ntwo", options);

        assertThat(chunks).containsExactly("one", "two");
    }

    @Test
    void regexModeRejectsInvalidPattern() {
        var options = new ChunkingOptions("regex", 1000, 100, null, "[", false);

        assertThatThrownBy(() -> chunker.chunk("text", options))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("切片正则不合法");
    }

    @Test
    void smartModeFallsBackToParagraphSplit() {
        var options = new ChunkingOptions("smart", 1000, 100, null, null, false);

        var chunks = chunker.chunk("para one\n\npara two\n\npara three", options);

        assertThat(chunks).containsExactly("para one", "para two", "para three");
    }

    @Test
    void lengthModeSplitsByParagraphsFirstThenByMaxLength() {
        // 按长度切片：先按空行粗分段落，短段落保持完整，不跨段落边界滑动切分
        var options = new ChunkingOptions("length", 1000, 100, null, null, false);

        var chunks = chunker.chunk("para one\n\npara two\n\npara three", options);

        assertThat(chunks).containsExactly("para one", "para two", "para three");
    }

    @Test
    void lengthModeSplitsLongParagraphBeyondMax() {
        // 超长段落再按最大长度滑动切分（带重叠），其余段落保持独立
        var options = new ChunkingOptions("length", 10, 2, null, null, false);

        var chunks = chunker.chunk("aaaa bbbb cccc\n\ndddd", options);

        assertThat(chunks).containsExactly("aaaa bbbb ", "b cccc", "dddd");
    }

    @Test
    void mergeShortSegmentsMergesShortChunksIntoNext() {
        // 分隔符切片：长段 + 短段 + 长段，短段（不足 max/2）应并入其后长段
        var options = new ChunkingOptions("delimiter", 100, 0, "\n\n", null, true);

        var chunks = chunker.chunk("a".repeat(100) + "\n\n" + "short" + "\n\n" + "b".repeat(100), options);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0)).isEqualTo("a".repeat(100));
        assertThat(chunks.get(1)).startsWith("short");
        assertThat(chunks.get(1)).endsWith("b".repeat(100));
    }

    @Test
    void mergeShortSegmentsWithoutMergeKeepsAllChunks() {
        var options = new ChunkingOptions("delimiter", 100, 0, "\n\n", null, false);

        var chunks = chunker.chunk("a".repeat(100) + "\n\n" + "short" + "\n\n" + "b".repeat(100), options);

        assertThat(chunks).hasSize(3);
        assertThat(chunks).extracting(String::length).containsExactly(100, 5, 100);
    }
}
