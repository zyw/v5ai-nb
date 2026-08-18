package xin.v5ai.nb.rag;

/**
 * 知识库下拉选项的轻量视图（仅需 id 与名称，避免加载描述等重字段）。
 *
 * @param id   主键
 * @param name 知识库名称
 */
public record KnowledgeBaseOption(Long id, String name) {
}
