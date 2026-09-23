package xin.v5ai.nb.common.agentscope.core.domain;

/**
 * 一个已绑定到 AgentDTO 的知识库引用（智能调用模式下用于把「可用知识库列表」写入系统提示，
 * 并在 {@code rag_search} 工具内校验模型传入的 {@code ragId}）。
 *
 * @param id          知识库 ID
 * @param name        知识库名称
 * @param description 知识库描述（可为 null）
 */
public record KnowledgeBaseRef(long id, String name, String description) {
}