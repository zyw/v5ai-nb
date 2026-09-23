package xin.v5ai.nb.rag.core.chunker;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.rag.core.ChunkMode;
import xin.v5ai.nb.rag.core.ChunkingOptions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ChunkPipeline} 单测：逐段定长切分与按需合并。
 *
 * @author ZYW
 * @since 2026-09-08
 */
class ChunkPipelineTest {

    private final ChunkPipeline pipeline = new ChunkPipeline(new FixedLengthSplitter(), new ChunkMerger());

    @Test
    void splitsEachSegmentByMaxLength() {
        var options = new ChunkingOptions(ChunkMode.LENGTH, 10, 2, null, null, false);

        var chunks = pipeline.run(List.of("aaaa bbbb cccc", "dddd"), options);

        assertThat(chunks).containsExactly("aaaa bbbb ", "b cccc", "dddd");
    }

    @Test
    void keepsAllChunksWhenMergeDisabled() {
        var options = new ChunkingOptions(ChunkMode.DELIMITER, 100, 0, "\n\n", null, false);

        var chunks = pipeline.run(List.of("a".repeat(100), "short", "b".repeat(100)), options);

        assertThat(chunks).hasSize(3);
        assertThat(chunks).extracting(String::length).containsExactly(100, 5, 100);
    }
}
