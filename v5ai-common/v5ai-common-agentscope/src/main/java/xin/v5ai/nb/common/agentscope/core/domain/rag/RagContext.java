package xin.v5ai.nb.common.agentscope.core.domain.rag;

import java.util.List;

/**
 * 一次运行的知识检索结果（RAG 上下文 + 结构化命中）。
 *
 * <p>上下文文本用于拼进系统提示；命中列表用于产出 {@code RETRIEVAL} 事件，
 * 供前端展示「本次引用了哪些切片」。两者来自同一次检索，避免二次查询。</p>
 *
 * <p>放在 common 侧而不是直接用 RAG 模块的检索结果类型：依赖方向是
 * rag → common-agentscope，common 不能反向依赖 rag。</p>
 *
 * @param context 检索上下文文本（无知识/无命中时为空串）
 * @param hits    结构化命中（无命中时为空列表）
 */
public record RagContext(String context, List<RagHit> hits) {

    public RagContext {
        hits = hits == null ? List.of() : List.copyOf(hits);
    }

    /** 无知识检索结果。 */
    public static RagContext empty() {
        return new RagContext("", List.of());
    }

    /** 上下文是否为空（空则运行时跳过 RAG，不注入系统提示）。 */
    public boolean isEmpty() {
        return context == null || context.isBlank();
    }
}
