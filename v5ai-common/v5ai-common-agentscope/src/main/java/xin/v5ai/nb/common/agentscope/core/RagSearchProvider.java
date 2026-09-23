package xin.v5ai.nb.common.agentscope.core;

import xin.v5ai.nb.common.agentscope.core.domain.KnowledgeBaseRef;
import xin.v5ai.nb.common.agentscope.core.domain.rag.RagContext;

import java.util.List;

/**
 * RAG 按库检索端口（智能调用用），与 {@link RagContextProvider} 区分：
 * 本端口面向 {@code rag_search} 工具，按「单个知识库 + 任意 query」检索，
 * 并回答「该 AgentDTO 绑定了哪些知识库」。
 *
 * 阻塞式签名与 {@code McpToolResolver} / {@code SkillWorkspaceResolver} 对齐，
 * 由 v5ai-rag 侧实现（{@code DBRagContextProvider}）。
 */
public interface RagSearchProvider {
    /**
     * 该 AgentDTO 绑定的知识库列表（用于 {@code rag_search} 的提示词与 {@code ragId} 校验）。
     */
    List<KnowledgeBaseRef> listBoundKnowledgeBases(String agentKey);

    /**
     * 在指定知识库内按 query 检索；知识库未绑定到该 AgentDTO 时抛出异常（工具侧转为友好错误）。
     */
    RagContext search(String agentKey, long knowledgeBaseId, String query);
}