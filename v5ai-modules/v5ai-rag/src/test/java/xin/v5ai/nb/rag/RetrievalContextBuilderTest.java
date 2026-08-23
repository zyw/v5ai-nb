package xin.v5ai.nb.rag;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.rag.core.*;
import xin.v5ai.nb.rag.core.repository.AgentKnowledgeBindingRepository;
import xin.v5ai.nb.rag.core.repository.KnowledgeDocumentRepository;
import xin.v5ai.nb.rag.core.store.VectorStore;
import xin.v5ai.nb.rag.domain.KnowledgeDocument;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalContextBuilderTest {
    @Test
    void returnsEmptyWhenNoKnowledgeBaseBound() {
        var builder = new RetrievalContextBuilder(
                new StubBindingRepository(List.of()),
                new StubVectorStore(),
                new StubEmbeddingClient(),
                new StubDocumentRepository());

        var result = builder.build("demo", "what is v5ai?");

        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void buildsContextFromVectorHitsWithReferences() {
        var vectorStore = new StubVectorStore();
        vectorStore.hits.add(new VectorStore.RetrievalHit(1L, 1L, 2, "v5ai is a platform", "{}", 0.95));
        var builder = new RetrievalContextBuilder(
                new StubBindingRepository(List.of(1L)),
                vectorStore,
                new StubEmbeddingClient(),
                new StubDocumentRepository());

        var result = builder.build("demo", "what is v5ai?");

        assertThat(result.context()).contains("guide.md")
                .contains("chunk 2")
                .contains("v5ai is a platform");
        assertThat(result.hits()).hasSize(1);
    }

    @Test
    void fallsBackToKeywordSearchWhenVectorSearchIsEmpty() {
        var vectorStore = new StubVectorStore();
        vectorStore.keywordHits.add(new VectorStore.RetrievalHit(1L, 1L, 0, "keyword match", "{}", 1.0));
        var builder = new RetrievalContextBuilder(
                new StubBindingRepository(List.of(1L)),
                vectorStore,
                new StubEmbeddingClient(),
                new StubDocumentRepository());

        var result = builder.build("demo", "match");

        assertThat(result.context()).contains("keyword match");
        assertThat(vectorStore.keywordSearched).isTrue();
    }

    private static final class StubDocumentRepository implements KnowledgeDocumentRepository {
        @Override
        public KnowledgeDocument save(KnowledgeDocument document) {
            return document;
        }

        @Override
        public KnowledgeDocument findById(Long id) {
            var document = new KnowledgeDocument();
            document.setId(1L);
            document.setKnowledgeBaseId(1L);
            document.setTitle("guide.md");
            document.setFileType("MARKDOWN");
            document.setSource("guide.md");
            document.setStatus("COMPLETED");
            return document;
        }

        @Override
        public List<KnowledgeDocument> findByKnowledgeBaseId(Long knowledgeBaseId) {
            return List.of();
        }

        @Override
        public void delete(Long id) {
        }
    }

    private static final class StubEmbeddingClient implements EmbeddingClient {
        @Override
        public int dimensions() {
            return 2;
        }

        @Override
        public List<Float> embed(String text) {
            return List.of(1.0f, 2.0f);
        }
    }

    private static final class StubBindingRepository implements AgentKnowledgeBindingRepository {
        private final List<Long> ids;

        private StubBindingRepository(List<Long> ids) {
            this.ids = ids;
        }

        @Override
        public void save(String agentKey, List<Long> knowledgeBaseIds) {
        }

        @Override
        public List<Long> findByAgentKey(String agentKey) {
            return ids;
        }

        @Override
        public void delete(String agentKey) {
        }
    }

    private static final class StubVectorStore implements VectorStore {
        private final List<RetrievalHit> hits = new java.util.ArrayList<>();
        private final List<RetrievalHit> keywordHits = new java.util.ArrayList<>();
        private boolean keywordSearched;

        @Override
        public void store(List<VectorChunk> chunks) {
        }

        @Override
        public void deleteByDocumentId(Long documentId) {
        }

        @Override
        public List<RetrievalHit> search(List<Long> knowledgeBaseIds, List<Float> queryVector, int topK) {
            return hits;
        }

        @Override
        public List<RetrievalHit> keywordSearch(List<Long> knowledgeBaseIds, String keyword, int topK) {
            keywordSearched = true;
            return keywordHits;
        }
    }
}
