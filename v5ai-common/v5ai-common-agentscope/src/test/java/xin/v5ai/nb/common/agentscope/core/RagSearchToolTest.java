package xin.v5ai.nb.common.agentscope.core;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.tool.ToolCallParam;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import xin.v5ai.nb.common.agentscope.core.domain.rag.RagContext;
import xin.v5ai.nb.common.agentscope.core.domain.rag.RagHit;
import xin.v5ai.nb.common.agentscope.core.tools.RagSearchTool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RAG 智能调用工具：成功检索（返回上下文 + 回传命中供 RETRIEVAL）与缺参/越权/空 query 的降级行为。
 */
class RagSearchToolTest {

    private static final RagHit HIT = new RagHit(1L, 2L, "doc", 0, "内容", 0.9);

    @Test
    void callReturnsContextAndPublishesHits() {
        var provider = Mockito.mock(RagSearchProvider.class);
        Mockito.when(provider.search("agent-x", 1L, "问题"))
                .thenReturn(new RagContext("[Knowledge Base Context]\n1. content", List.of(HIT)));
        var published = new ArrayList<List<RagHit>>();
        var tool = new RagSearchTool("agent-x", provider, published::add);
        var param = ToolCallParam.builder().input(Map.of("ragId", 1, "queryQuestion", "问题")).build();

        var block = tool.callAsync(param).block();

        assertThat(block).isNotNull();
        assertThat(block.getOutput()).hasSize(1);
        assertThat(((TextBlock) block.getOutput().get(0)).getText()).isEqualTo("[Knowledge Base Context]\n1. content");
        assertThat(published).hasSize(1);
        assertThat(published.get(0)).extracting(RagHit::documentTitle).containsExactly("doc");
        Mockito.verify(provider).search("agent-x", 1L, "问题");
    }

    @Test
    void callErrorsWhenArgumentsMissing() {
        var tool = new RagSearchTool("agent-x", Mockito.mock(RagSearchProvider.class), hits -> {
        });

        var block = tool.callAsync(ToolCallParam.builder().input(Map.of()).build()).block();

        assertThat(block).isNotNull();
        assertThat(block.getState()).isEqualTo(ToolResultState.ERROR);
    }

    @Test
    void callErrorsWhenQueryBlank() {
        var tool = new RagSearchTool("agent-x", Mockito.mock(RagSearchProvider.class), hits -> {
        });
        var param = ToolCallParam.builder().input(Map.of("ragId", 1, "queryQuestion", " ")).build();

        var block = tool.callAsync(param).block();

        assertThat(block).isNotNull();
        assertThat(block.getState()).isEqualTo(ToolResultState.ERROR);
    }

    @Test
    void callErrorsWhenKnowledgeBaseNotBound() {
        var provider = Mockito.mock(RagSearchProvider.class);
        Mockito.when(provider.search(Mockito.anyString(), Mockito.anyLong(), Mockito.anyString()))
                .thenThrow(new IllegalArgumentException("not bound"));
        var tool = new RagSearchTool("agent-x", provider, hits -> {
        });
        var param = ToolCallParam.builder().input(Map.of("ragId", 9, "queryQuestion", "问题")).build();

        var block = tool.callAsync(param).block();

        assertThat(block).isNotNull();
        assertThat(block.getState()).isEqualTo(ToolResultState.ERROR);
    }
}