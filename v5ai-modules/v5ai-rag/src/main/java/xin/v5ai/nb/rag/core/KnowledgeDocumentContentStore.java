package xin.v5ai.nb.rag.core;

/**
 * 加载和保存文档原始内容（BYTEA）的端口（Worker 索引链路消费，由模块内 MyBatis 实现提供）。
 */
public interface KnowledgeDocumentContentStore {
    byte[] loadContent(Long documentId);

    void saveContent(Long documentId, byte[] content);
}
