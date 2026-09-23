package xin.v5ai.nb.runtime.core;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xin.v5ai.nb.common.agentscope.core.AgentTextEvent;
import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentDTO;
import xin.v5ai.nb.common.agentscope.core.domain.bo.AgentRunBo;
import xin.v5ai.nb.common.agentscope.core.domain.dto.RuntimeRunEventDTO;
import xin.v5ai.nb.common.agentscope.core.executor.AgentTextExecutor;
import xin.v5ai.nb.common.agentscope.core.domain.rag.RagContext;
import xin.v5ai.nb.common.agentscope.core.domain.rag.RagHit;
import xin.v5ai.nb.common.agentscope.enums.RagCallMode;
import xin.v5ai.nb.common.agentscope.enums.RuntimeEventType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RAG 引用链路：运行时把结构化命中转成 {@code RETRIEVAL} 事件载荷。
 *
 * 载荷字段名必须与知识库问答（{@code KnowledgeQaServiceImpl.hitsPayload}）一致，
 * 前端才能复用同一个解析器与渲染组件（见 CONTEXT.md「引用」）。
 * 断言按解析后的字段做，不比较序列化键序——键序对消费方无意义。
 */
class AgentScopeRuntimeCitationsTest {

    /** 不跑模型，只回放一个文本片段，便于把事件流收干。 */
    private static final class NoopExecutor implements AgentTextExecutor {
        @Override
        public Flux<AgentTextEvent> streamText(AgentDTO agent, AgentRunBo request) {
            return Flux.just(new AgentTextEvent.Text("ok"));
        }
    }

    /** 构造开启 RAG 的 AgentDTO（能力开关默认 false，必须显式开启才会构建检索上下文）。 */
    private static AgentDTO agentWithRag() {
        return new AgentDTO("agent-x", "Demo", "desc", null, 1L, null,
                null, null, null, null,
                false, false, false, false, true, RagCallMode.FORCED.value(), null, true);
    }

    private static AgentScopeRuntime runtimeWith(RagContext rag) {
        return new AgentScopeRuntime(
                key -> agentWithRag(),
                new NoopExecutor(),
                (agent, request) -> Mono.just(rag));
    }

    private static String retrievalPayloadOf(RagContext rag) {
        var events = runtimeWith(rag)
                .stream(new AgentRunBo("agent-x", "conv-1", "问题"))
                .collectList().block();
        return events.stream()
                .filter(event -> event.type() == RuntimeEventType.RETRIEVAL)
                .map(RuntimeRunEventDTO::payload)
                .findFirst()
                .orElseThrow(() -> new AssertionError("未发出 RETRIEVAL 事件：" + events));
    }

    @Test
    void retrievalPayloadCarriesTheFieldsTheCitationUiReads() {
        var rag = new RagContext("上下文文本", List.of(
                new RagHit(2L, 7L, "产品原型设计文档.pdf", 3, "切片内容", 0.8123)));

        JSONArray payload = JSONUtil.parseArray(retrievalPayloadOf(rag));

        assertThat(payload).hasSize(1);
        JSONObject item = payload.getJSONObject(0);
        // 与知识库问答的同构字段集：多一个少一个都会让前端渲染器失配
        assertThat(item.keySet()).containsExactlyInAnyOrder(
                "knowledgeBaseId", "documentId", "documentTitle", "chunkIndex", "content", "score");
        assertThat(item.getLong("knowledgeBaseId")).isEqualTo(2L);
        assertThat(item.getLong("documentId")).isEqualTo(7L);
        assertThat(item.getStr("documentTitle")).isEqualTo("产品原型设计文档.pdf");
        assertThat(item.getInt("chunkIndex")).isEqualTo(3);
        assertThat(item.getStr("content")).isEqualTo("切片内容");
        assertThat(item.getDouble("score")).isEqualTo(0.8123);
    }

    @Test
    void retrievalPayloadTruncatesContentLikeKnowledgeQaDoes() {
        var longContent = "字".repeat(2500);
        var rag = new RagContext("上下文文本", List.of(
                new RagHit(2L, 7L, "标题", 0, longContent, 0.5)));

        JSONArray payload = JSONUtil.parseArray(retrievalPayloadOf(rag));
        String content = payload.getJSONObject(0).getStr("content");

        // 与知识库问答同一个 HIT_CONTENT_CAP(2000)：注意 StrUtil.maxLength 的语义是
        // 「超长时截断到上限再补 "..."」，所以结果是 2000 字符 + 省略号，而非恰好 2000。
        // 这里保持与知识库问答完全一致（同一常量、同一函数），不单独修一边。
        assertThat(content).hasSize(2003);
        assertThat(content).startsWith("字".repeat(2000));
        assertThat(content).endsWith("...");
    }

    @Test
    void noRetrievalEventWhenContextIsEmpty() {
        var events = runtimeWith(RagContext.empty())
                .stream(new AgentRunBo("agent-x", "conv-1", "问题"))
                .collectList().block();

        assertThat(events).isNotNull();
        assertThat(events).noneMatch(event -> event.type() == RuntimeEventType.RETRIEVAL);
        assertThat(events).anyMatch(event -> event.type() == RuntimeEventType.TEXT_DELTA);
    }
}