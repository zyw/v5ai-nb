package xin.v5ai.nb.rag.core.chunker;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ChunkMerger} 单测：短片段并入其后片段。
 *
 * @author ZYW
 * @since 2026-09-08
 */
class ChunkMergerTest {

    private final ChunkMerger merger = new ChunkMerger();

    @Test
    void mergesShortChunksIntoNext() {
        // 长段 + 短段 + 长段，短段（不足 max/2）应并入其后长段
        var chunks = merger.merge(List.of("a".repeat(100), "short", "b".repeat(100)), 100);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0)).isEqualTo("a".repeat(100));
        assertThat(chunks.get(1)).startsWith("short");
        assertThat(chunks.get(1)).endsWith("b".repeat(100));
    }
}
