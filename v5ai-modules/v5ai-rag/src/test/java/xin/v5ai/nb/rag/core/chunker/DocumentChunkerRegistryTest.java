package xin.v5ai.nb.rag.core.chunker;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.agentscope.core.ModelChatClient;
import xin.v5ai.nb.rag.core.ChunkMode;
import xin.v5ai.nb.rag.core.ChunkingOptions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link DocumentChunkerRegistry} 单测：按切片策略路由到对应实现类。
 *
 * @author ZYW
 * @since 2026-09-08
 */
class DocumentChunkerRegistryTest {

    private final DocumentChunkerRegistry registry = new DocumentChunkerRegistry(List.of(
            new LengthDocumentChunker(pipeline()),
            new DelimiterDocumentChunker(pipeline()),
            new RegexDocumentChunker(pipeline()),
            new SmartDocumentChunker(pipeline(), mock(ModelChatClient.class))));

    private static ChunkPipeline pipeline() {
        return new ChunkPipeline(new FixedLengthSplitter(), new ChunkMerger());
    }

    @Test
    void routesBySliceStrategy() {
        // delimiter 按 \n 一级切分 → 两段；length/smart 无空行 → 整段折叠为一段
        var delimiter = new ChunkingOptions(ChunkMode.DELIMITER, 1000, 100, "[\"\\n\"]", null, false);
        assertThat(registry.chunk("x\ny", delimiter)).containsExactly("x", "y");

        var length = new ChunkingOptions(ChunkMode.LENGTH, 1000, 100, null, null, false);
        assertThat(registry.chunk("x\ny", length)).containsExactly("x y");

        var smart = new ChunkingOptions(ChunkMode.SMART, 1000, 100, null, null, false);
        assertThat(registry.chunk("x\ny", smart)).containsExactly("x y");
    }

    @Test
    void defaultStrategyWhenSliceStrategyNull() {
        assertThat(registry.chunk("para one\n\npara two", ChunkingOptions.DEFAULT))
                .containsExactly("para one", "para two");
    }

    @Test
    void chunkWithoutOptionsUsesFallback() {
        assertThat(registry.chunk("a".repeat(2000))).hasSize(3);
    }
}
