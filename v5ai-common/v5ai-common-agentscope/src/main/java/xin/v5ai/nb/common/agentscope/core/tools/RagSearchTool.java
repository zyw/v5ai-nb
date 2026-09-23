package xin.v5ai.nb.common.agentscope.core.tools;

import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import xin.v5ai.nb.common.agentscope.core.RagSearchProvider;
import xin.v5ai.nb.common.agentscope.core.domain.rag.RagHit;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * RAG 智能调用工具：按知识库 ID + 问题检索，返回纯文本上下文。
 *
 * <p>参数与 {@code WebSearchTool} 相同机制——用 {@code inputSchema} 的 JSON Schema 声明，
 * 而非 {@code @ToolParam}（本仓库 {@code ToolBase} 约定）：{@code ragId}（integer，必填）
 * 与 {@code queryQuestion}（string，必填）。</p>
 *
 * <p>命中通过 {@code hitsConsumer} 回传（由执行器在 {@code TOOL_RESULT} 之后追加
 * {@code RETRIEVAL} 事件），工具本身只返回给模型的纯文本上下文。</p>
 */
public class RagSearchTool extends ToolBase {
    public static final String TOOL_NAME = "rag_search";

    private final String agentKey;
    private final RagSearchProvider provider;
    private final Consumer<List<RagHit>> hitsConsumer;

    public RagSearchTool(String agentKey, RagSearchProvider provider, Consumer<List<RagHit>> hitsConsumer) {
        super(ToolBase.builder()
                .name(TOOL_NAME)
                .description("Search a knowledge base bound to this agent by its id, using the given question. "
                        + "Call it with 'ragId' (the knowledge base id) and 'queryQuestion' (what to look up).")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "ragId", Map.of("type", "integer", "description", "The ID of the knowledge base to search"),
                                "queryQuestion", Map.of("type", "string", "description", "The user's question or related query")),
                        "required", List.of("ragId", "queryQuestion")))
                .readOnly(true));
        this.agentKey = agentKey;
        this.provider = provider;
        this.hitsConsumer = hitsConsumer;
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        var input = param.getInput();
        if (input == null || input.get("ragId") == null || input.get("queryQuestion") == null) {
            return Mono.just(ToolResultBlock.error("rag_search requires 'ragId' and 'queryQuestion' arguments"));
        }
        long ragId;
        try {
            ragId = ((Number) input.get("ragId")).longValue();
        } catch (ClassCastException exception) {
            return Mono.just(ToolResultBlock.error("rag_search 'ragId' must be a number"));
        }
        String query = String.valueOf(input.get("queryQuestion"));
        if (query.isBlank()) {
            return Mono.just(ToolResultBlock.error("rag_search 'queryQuestion' must not be empty"));
        }
        return Mono.fromCallable(() -> {
                    var context = provider.search(agentKey, ragId, query);
                    if (hitsConsumer != null) {
                        hitsConsumer.accept(context.hits());
                    }
                    return ToolResultBlock.text(context.isEmpty() ? "No relevant content found." : context.context());
                })
                .onErrorResume(exception -> Mono.just(
                        ToolResultBlock.error("rag_search failed: " + exception.getMessage())))
                .subscribeOn(Schedulers.boundedElastic());
    }
}