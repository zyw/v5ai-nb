package xin.v5ai.nb.rag.domain.bo;

import java.util.List;

/**
 * AgentDTO 绑定知识库请求体。
 *
 * @param knowledgeBaseIds 知识库 ID 集合
 */
public record KnowledgeBindBo(List<Long> knowledgeBaseIds) {
}
