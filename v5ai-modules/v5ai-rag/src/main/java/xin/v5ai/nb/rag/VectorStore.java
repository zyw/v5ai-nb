package xin.v5ai.nb.rag;

import java.util.List;

/**
 * Vector persistence and similarity search. A chunk is stored together with its
 * embedding and its retrieval metadata.
 */
public interface VectorStore {

    record VectorChunk(Long knowledgeBaseId, Long documentId, int chunkIndex, String content,
                       List<Float> embedding, String metadata) {
    }

    record RetrievalHit(Long knowledgeBaseId, Long documentId, int chunkIndex, String content, String metadata,
                        double score) {
    }

    void store(List<VectorChunk> chunks);

    void deleteByDocumentId(Long documentId);

    List<RetrievalHit> search(List<Long> knowledgeBaseIds, List<Float> queryVector, int topK);

    List<RetrievalHit> keywordSearch(List<Long> knowledgeBaseIds, String keyword, int topK);
}
