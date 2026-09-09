package xin.v5ai.nb.rag.core.chunker;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.agentscope.core.ModelChatClient;
import xin.v5ai.nb.rag.core.ChunkMode;
import xin.v5ai.nb.rag.core.ChunkingOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link SmartDocumentChunker} 单测：模型成功返回语义片段走流水线；模型缺失/失败/输出不可解析则回退按段落切分。
 *
 * @author ZYW
 * @since 2026-09-08
 */
class SmartDocumentChunkerTest {

    private final ModelChatClient modelChatClient = mock(ModelChatClient.class);
    private final SmartDocumentChunker chunker = new SmartDocumentChunker(
            new ChunkPipeline(new FixedLengthSplitter(), new ChunkMerger()), modelChatClient);

    @Test
    void fallsBackToParagraphSplitWhenNoModelConfigured() {
        var options = new ChunkingOptions(ChunkMode.SMART, 1000, 100, null, null, false, null);

        var chunks = chunker.chunk("para one\n\npara two\n\npara three", options);

        assertThat(chunks).containsExactly("para one", "para two", "para three");
    }

    @Test
    void usesModelSegmentsThenPipeline() {
        when(modelChatClient.chatText(eq(42L), any(), any()))
                .thenReturn("[\"first bit\", \"second bit\"]");
        var options = new ChunkingOptions(ChunkMode.SMART, 1000, 100, null, null, false, 42L);

        var chunks = chunker.chunk("ignored because the model returns the segments", options);

        assertThat(chunks).containsExactly("first bit", "second bit");
    }

    @Test
    void stripsCodeFenceFromModelOutput() {
        when(modelChatClient.chatText(eq(42L), any(), any()))
                .thenReturn("```json\n[\"a\", \"b\"]\n```");
        var options = new ChunkingOptions(ChunkMode.SMART, 1000, 100, null, null, false, 42L);

        assertThat(chunker.chunk("whatever", options)).containsExactly("a", "b");
    }

    @Test
    void fallsBackOnModelFailure() {
        when(modelChatClient.chatText(eq(42L), any(), any()))
                .thenThrow(new RuntimeException("boom"));
        var options = new ChunkingOptions(ChunkMode.SMART, 1000, 100, null, null, false, 42L);

        assertThat(chunker.chunk("para one\n\npara two", options)).containsExactly("para one", "para two");
    }

    @Test
    void fallsBackOnInvalidJson() {
        when(modelChatClient.chatText(eq(42L), any(), any()))
                .thenReturn("definitely not valid json");
        var options = new ChunkingOptions(ChunkMode.SMART, 1000, 100, null, null, false, 42L);

        assertThat(chunker.chunk("para one\n\npara two", options)).containsExactly("para one", "para two");
    }

    @Test
    void parseSemanticSegmentsKeepsStringsDropsOthers() {
        assertThat(SmartDocumentChunker.parseSemanticSegments("[\"a\", \"  \", \"\", 123, null, \"b\"]"))
                .containsExactly("a", "b");
    }
}