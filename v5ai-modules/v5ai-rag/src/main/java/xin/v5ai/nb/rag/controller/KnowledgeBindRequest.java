package xin.v5ai.nb.rag.controller;

import java.util.List;

/**
 * Agent 绑定知识库请求体。
 *
 * @param knowledgeBaseIds 知识库 ID 集合
 */
public record KnowledgeBindRequest(List<Long> knowledgeBaseIds) {
}
