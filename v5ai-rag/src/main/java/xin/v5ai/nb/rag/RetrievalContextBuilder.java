package xin.v5ai.nb.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds the retrieval context for a runtime request: resolves the knowledge bases
 * bound to the application, embeds the query, performs vector search (with a keyword
 * fallback) and formats the hits into a prompt context with source references.
 */
@Component
public class RetrievalContextBuilder {
    private static final int DEFAULT_TOP_K = 4;

    private final AgentKnowledgeBindingRepository bindingRepository;
    private final VectorStore vectorStore;
    private final EmbeddingClient embeddingClient;
    private final KnowledgeDocumentRepository documentRepository;

    public RetrievalContextBuilder(
            AgentKnowledgeBindingRepository bindingRepository,
            VectorStore vectorStore,
            EmbeddingClient embeddingClient,
            KnowledgeDocumentRepository documentRepository
    ) {
        this.bindingRepository = bindingRepository;
        this.vectorStore = vectorStore;
        this.embeddingClient = embeddingClient;
        this.documentRepository = documentRepository;
    }

    public RetrievalResult build(String agentKey, String query) {
        var knowledgeBaseIds = bindingRepository.findByAgentKey(agentKey);
        if (knowledgeBaseIds.isEmpty()) {
            return RetrievalResult.empty();
        }
        List<VectorStore.RetrievalHit> hits;
        try {
            var queryVector = embeddingClient.embed(query);
            hits = vectorStore.search(knowledgeBaseIds, queryVector, DEFAULT_TOP_K);
        } catch (Exception exception) {
            hits = List.of();
        }
        if (hits.isEmpty()) {
            hits = vectorStore.keywordSearch(knowledgeBaseIds, query, DEFAULT_TOP_K);
        }
        if (hits.isEmpty()) {
            return RetrievalResult.empty();
        }
        return new RetrievalResult(formatContext(hits), hits);
    }

    private String formatContext(List<VectorStore.RetrievalHit> hits) {
        var lines = new ArrayList<String>();
        lines.add("[Knowledge Base Context]");
        int index = 1;
        for (var hit : hits) {
            var document = documentRepository.findById(hit.documentId());
            var title = document == null ? "doc-" + hit.documentId() : document.title();
            lines.add("%d. (%s, chunk %d): %s".formatted(index++, title, hit.chunkIndex(), hit.content()));
        }
        return lines.stream().collect(Collectors.joining("\n"));
    }
}
