package xin.v5ai.nb.rag;

/**
 * Loads and stores the raw document content (BYTEA) for knowledge indexing.
 */
public interface KnowledgeDocumentContentStore {
    byte[] loadContent(Long documentId);

    void saveContent(Long documentId, byte[] content);
}
