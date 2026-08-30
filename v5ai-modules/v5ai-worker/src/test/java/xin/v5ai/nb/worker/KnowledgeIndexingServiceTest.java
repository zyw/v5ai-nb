package xin.v5ai.nb.worker;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.rag.core.store.VectorStore;
import xin.v5ai.nb.rag.core.ChunkingOptions;
import xin.v5ai.nb.rag.core.DocumentChunker;
import xin.v5ai.nb.rag.core.EmbeddingClient;
import xin.v5ai.nb.rag.core.KnowledgeDocumentContentStore;
import xin.v5ai.nb.rag.core.repository.KnowledgeBaseRepository;
import xin.v5ai.nb.rag.core.repository.KnowledgeDocumentRepository;
import xin.v5ai.nb.rag.core.repository.KnowledgeTaskRepository;
import xin.v5ai.nb.rag.domain.KnowledgeBase;
import xin.v5ai.nb.rag.domain.KnowledgeDocument;
import xin.v5ai.nb.rag.domain.KnowledgeTask;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeIndexingServiceTest {
    @Test
    void processTaskParsesChunksEmbedsStoresAndCompletes() {
        var taskRepository = new RecordingTaskRepository();
        taskRepository.stored = task(1L, 1L, 10L, "PENDING", 0);
        var documentRepository = new RecordingDocumentRepository();
        documentRepository.stored = document(10L, 1L, "guide.md", "MARKDOWN", "PENDING");
        var vectorStore = new RecordingVectorStore();
        var service = new KnowledgeIndexingService(taskRepository, documentRepository, new RecordingKnowledgeBaseRepository(),
                new FixedContentStore("hello world knowledge".getBytes(StandardCharsets.UTF_8)),
                (content, type) -> new String(content, StandardCharsets.UTF_8),
                text -> java.util.List.of("hello world", "knowledge"),
                new FixedEmbeddingClient(java.util.List.of(1.0f, 2.0f)),
                vectorStore);

        service.processTask(1L);

        assertThat(vectorStore.stored).hasSize(2);
        assertThat(vectorStore.stored.getFirst().content()).isEqualTo("hello world");
        assertThat(vectorStore.stored.getFirst().embedding()).containsExactly(1.0f, 2.0f);
        assertThat(documentRepository.saved.getLast().getStatus()).isEqualTo("COMPLETED");
        assertThat(taskRepository.saved.getLast().getStatus()).isEqualTo("COMPLETED");
        assertThat(taskRepository.saved.getLast().getAttemptCount()).isEqualTo(1);
    }

    @Test
    void processTaskUsesKnowledgeBaseChunkConfig() {
        var taskRepository = new RecordingTaskRepository();
        taskRepository.stored = task(1L, 5L, 10L, "PENDING", 0);
        var documentRepository = new RecordingDocumentRepository();
        documentRepository.stored = document(10L, 5L, "guide.md", "MARKDOWN", "PENDING");
        var kbRepository = new RecordingKnowledgeBaseRepository();
        kbRepository.stored = knowledgeBase(5L,
                "{\"chunkParams\":{\"mode\":\"delimiter\",\"maxChunkTokens\":300,\"customDelimiter\":\"\\n\\n\",\"mergeShortSegments\":true}}");
        var chunker = new RecordingChunker();
        var service = new KnowledgeIndexingService(taskRepository, documentRepository, kbRepository,
                new FixedContentStore("a\n\nb".getBytes(StandardCharsets.UTF_8)),
                (content, type) -> new String(content, StandardCharsets.UTF_8),
                chunker,
                new FixedEmbeddingClient(List.of(1.0f)),
                new RecordingVectorStore());

        service.processTask(1L);

        assertThat(chunker.receivedOptions).hasSize(1);
        assertThat(chunker.receivedOptions.get(0).mode()).isEqualTo("delimiter");
        assertThat(chunker.receivedOptions.get(0).maxChunkTokens()).isEqualTo(300);
        assertThat(chunker.receivedOptions.get(0).customDelimiter()).isEqualTo("\n\n");
        assertThat(chunker.receivedOptions.get(0).mergeShortSegments()).isTrue();
    }

    @Test
    void processTaskMarksFailedAndRecordsErrorWhenParsingThrows() {
        var taskRepository = new RecordingTaskRepository();
        taskRepository.stored = task(1L, 1L, 10L, "PENDING", 0);
        var documentRepository = new RecordingDocumentRepository();
        documentRepository.stored = document(10L, 1L, "broken.pdf", "PDF", "PENDING");
        var service = new KnowledgeIndexingService(taskRepository, documentRepository, new RecordingKnowledgeBaseRepository(),
                new FixedContentStore(new byte[0]),
                (content, type) -> { throw new IllegalStateException("parse boom"); },
                text -> List.of("x"),
                new FixedEmbeddingClient(List.of(1.0f)),
                new RecordingVectorStore());

        service.processTask(1L);

        assertThat(taskRepository.saved.getLast().getStatus()).isEqualTo("FAILED");
        assertThat(taskRepository.saved.getLast().getErrorMessage()).contains("parse boom");
        assertThat(taskRepository.saved.getLast().getAttemptCount()).isEqualTo(1);
        assertThat(documentRepository.saved.getLast().getStatus()).isEqualTo("FAILED");
    }

    @Test
    void processTaskIgnoresUnknownTaskOrDocument() {
        var taskRepository = new RecordingTaskRepository();
        var documentRepository = new RecordingDocumentRepository();
        var service = new KnowledgeIndexingService(taskRepository, documentRepository, new RecordingKnowledgeBaseRepository(),
                new FixedContentStore(new byte[0]), (content, type) -> "", text -> List.of(),
                new FixedEmbeddingClient(List.of(1.0f)),
                new RecordingVectorStore());

        service.processTask(99L);

        assertThat(taskRepository.saved).isEmpty();
    }

    private static KnowledgeTask task(Long id, Long knowledgeBaseId, Long documentId, String status, int attempts) {
        var task = new KnowledgeTask();
        task.setId(id);
        task.setKnowledgeBaseId(knowledgeBaseId);
        task.setDocumentId(documentId);
        task.setTaskType("PARSE_AND_INDEX");
        task.setStatus(status);
        task.setAttemptCount(attempts);
        task.setMaxAttempts(3);
        return task;
    }

    private static KnowledgeDocument document(Long id, Long knowledgeBaseId, String title, String fileType, String status) {
        var document = new KnowledgeDocument();
        document.setId(id);
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setTitle(title);
        document.setFileType(fileType);
        document.setSource(title);
        document.setStatus(status);
        return document;
    }

    private static KnowledgeBase knowledgeBase(Long id, String config) {
        var base = new KnowledgeBase();
        base.setId(id);
        base.setName("kb-" + id);
        base.setConfig(config);
        return base;
    }

    private static final class RecordingChunker implements DocumentChunker {
        private final List<ChunkingOptions> receivedOptions = new ArrayList<>();

        @Override
        public List<String> chunk(String text) {
            return List.of(text);
        }

        @Override
        public List<String> chunk(String text, ChunkingOptions options) {
            receivedOptions.add(options);
            return chunk(text);
        }
    }

    private static final class FixedEmbeddingClient implements EmbeddingClient {
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

    private static final class FixedContentStore implements KnowledgeDocumentContentStore {
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

    private static final class RecordingKnowledgeBaseRepository implements KnowledgeBaseRepository {
        private KnowledgeBase stored;

        @Override
        public KnowledgeBase findById(Long id) {
            return stored;
        }
    }

    private static final class RecordingTaskRepository implements KnowledgeTaskRepository {
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

    private static final class RecordingDocumentRepository implements KnowledgeDocumentRepository {
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
