package xin.v5ai.nb.rag.controller;

/**
 * 创建知识库请求体。
 *
 * @param name        知识库名称（必填）
 * @param description 知识库描述（可选）
 */
public record CreateKnowledgeBaseRequest(String name, String description) {
}
