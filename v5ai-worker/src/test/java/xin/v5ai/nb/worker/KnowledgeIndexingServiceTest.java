package xin.v5ai.nb.worker;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.rag.DocumentFileType;
import xin.v5ai.nb.rag.DocumentStatus;
import xin.v5ai.nb.rag.KnowledgeDocument;
import xin.v5ai.nb.rag.KnowledgeTask;
import xin.v5ai.nb.rag.TaskStatus;
import xin.v5ai.nb.rag.VectorStore;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeIndexingServiceTest {
    @Test
    void processTaskParsesChunksEmbedsStoresAndCompletes() {
        var taskRepository = new RecordingTaskRepository();
        taskRepository.stored = new KnowledgeTask(1L, 1L, 10L, TaskStatus.PENDING, 0, 3, null);
        var documentRepository = new RecordingDocumentRepository();
        documentRepository.stored = new KnowledgeDocument(10L, 1L, "guide.md", DocumentFileType.MARKDOWN,
                "guide.md", DocumentStatus.PENDING, null);
        var vectorStore = new RecordingVectorStore();
        var service = new KnowledgeIndexingService(taskRepository, documentRepository,
                new FixedContentStore("hello world knowledge".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                (content, type) -> new String(content, java.nio.charset.StandardCharsets.UTF_8),
                text -> java.util.List.of("hello world", "knowledge"),
                new FixedEmbeddingClient(java.util.List.of(1.0f, 2.0f)),
                vectorStore);

        service.processTask(1L);

        assertThat(vectorStore.stored).hasSize(2);
        assertThat(vectorStore.stored.getFirst().content()).isEqualTo("hello world");
        assertThat(vectorStore.stored.getFirst().embedding()).containsExactly(1.0f, 2.0f);
        assertThat(documentRepository.saved.getLast().status()).isEqualTo(DocumentStatus.COMPLETED);
        assertThat(taskRepository.saved.getLast().status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(taskRepository.saved.getLast().attemptCount()).isEqualTo(1);
    }

    @Test
    void processTaskMarksFailedAndRecordsErrorWhenParsingThrows() {
        var taskRepository = new RecordingTaskRepository();
        taskRepository.stored = new KnowledgeTask(1L, 1L, 10L, TaskStatus.PENDING, 0, 3, null);
        var documentRepository = new RecordingDocumentRepository();
        documentRepository.stored = new KnowledgeDocument(10L, 1L, "broken.pdf", DocumentFileType.PDF,
                "broken.pdf", DocumentStatus.PENDING, null);
        var service = new KnowledgeIndexingService(taskRepository, documentRepository,
                new FixedContentStore(new byte[0]),
                (content, type) -> { throw new IllegalStateException("parse boom"); },
                text -> List.of("x"),
                new FixedEmbeddingClient(List.of(1.0f)),
                new RecordingVectorStore());

        service.processTask(1L);

        assertThat(taskRepository.saved.getLast().status()).isEqualTo(TaskStatus.FAILED);
        assertThat(taskRepository.saved.getLast().errorMessage()).contains("parse boom");
        assertThat(taskRepository.saved.getLast().attemptCount()).isEqualTo(1);
        assertThat(documentRepository.saved.getLast().status()).isEqualTo(DocumentStatus.FAILED);
    }

    @Test
    void processTaskIgnoresUnknownTaskOrDocument() {
        var taskRepository = new RecordingTaskRepository();
        var documentRepository = new RecordingDocumentRepository();
        var service = new KnowledgeIndexingService(taskRepository, documentRepository,
                new FixedContentStore(new byte[0]), (content, type) -> "", text -> List.of(),
                new FixedEmbeddingClient(List.of(1.0f)),
                new RecordingVectorStore());

        service.processTask(99L);

        assertThat(taskRepository.saved).isEmpty();
    }

    private static final class FixedEmbeddingClient implements xin.v5ai.nb.rag.EmbeddingClient {
        private final List<Float> vector;

        private FixedEmbeddingClient(List<Float> vector) {
            this.vector = vector;
        }

        @Override
        public int dimensions() {
            return vector.size();
        }

        @Override
        public List<Float> embed(String text) {
            return vector;
        }
    }

    private static final class FixedContentStore implements xin.v5ai.nb.rag.KnowledgeDocumentContentStore {
        private final byte[] content;

        private FixedContentStore(byte[] content) {
            this.content = content;
        }

        @Override
        public byte[] loadContent(Long documentId) {
            return content;
        }

        @Override
        public void saveContent(Long documentId, byte[] content) {
        }
    }

    private static final class RecordingTaskRepository implements xin.v5ai.nb.rag.KnowledgeTaskRepository {
        private final List<KnowledgeTask> saved = new ArrayList<>();
        private KnowledgeTask stored;

        @Override
        public KnowledgeTask save(KnowledgeTask task) {
            saved.add(task);
            stored = task;
            return task;
        }

        @Override
        public KnowledgeTask findById(Long id) {
            return stored;
        }

        @Override
        public List<KnowledgeTask> findByDocumentId(Long documentId) {
            return List.of();
        }

        @Override
        public List<KnowledgeTask> findByKnowledgeBaseId(Long knowledgeBaseId) {
            return List.of();
        }

        @Override
        public void deleteByDocumentId(Long documentId) {
        }

        @Override
        public List<KnowledgeTask> findProcessable(int limit) {
            return List.of();
        }
    }

    private static final class RecordingDocumentRepository implements xin.v5ai.nb.rag.KnowledgeDocumentRepository {
        private final List<KnowledgeDocument> saved = new ArrayList<>();
        private KnowledgeDocument stored;

        @Override
        public KnowledgeDocument save(KnowledgeDocument document) {
            saved.add(document);
            stored = document;
            return document;
        }

        @Override
        public KnowledgeDocument findById(Long id) {
            return stored;
        }

        @Override
        public List<KnowledgeDocument> findByKnowledgeBaseId(Long knowledgeBaseId) {
            return List.of();
        }

        @Override
        public void delete(Long id) {
        }
    }

    private static final class RecordingVectorStore implements VectorStore {
        private final List<VectorChunk> stored = new ArrayList<>();

        @Override
        public void store(List<VectorChunk> chunks) {
            stored.addAll(chunks);
        }

        @Override
        public void deleteByDocumentId(Long documentId) {
        }

        @Override
        public List<RetrievalHit> search(List<Long> knowledgeBaseIds, List<Float> queryVector, int topK) {
            return List.of();
        }

        @Override
        public List<RetrievalHit> keywordSearch(List<Long> knowledgeBaseIds, String keyword, int topK) {
            return List.of();
        }
    }
}
