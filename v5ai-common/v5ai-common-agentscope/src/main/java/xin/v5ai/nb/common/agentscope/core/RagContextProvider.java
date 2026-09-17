package xin.v5ai.nb.common.agentscope.core;

import reactor.core.publisher.Mono;
import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentDTO;
import xin.v5ai.nb.common.agentscope.core.domain.bo.AgentRunBo;
import xin.v5ai.nb.common.agentscope.core.domain.rag.RagContext;

/**
 * RAG 上下文构建器：为一次运行请求生成检索上下文与结构化命中（知识库引用）。
 *
 * 实现约定：当 Agent 未绑定知识库或未配置检索时，返回 {@link RagContext#empty()}，
 * 运行时将直接跳过 RAG，正常执行 Agent。
 */
public interface RagContextProvider {
    /**
     * 异步构建检索结果。
     *
     * @param application 已解析的 Agent 配置（用于确定绑定的知识库等）
     * @param request     本次运行请求（可从中取用户提问作为检索 query）
     * @return 检索上下文 + 结构化命中；无知识/无命中时返回 {@link RagContext#empty()}
     */
    Mono<RagContext> buildContext(AgentDTO application, AgentRunBo request);
}