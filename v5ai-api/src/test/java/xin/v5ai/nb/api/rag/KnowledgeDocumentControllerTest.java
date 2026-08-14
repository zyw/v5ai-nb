package xin.v5ai.nb.api.rag;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.rag.DocumentFileType;
import xin.v5ai.nb.rag.DocumentStatus;
import xin.v5ai.nb.rag.KnowledgeBase;
import xin.v5ai.nb.rag.KnowledgeBaseStatus;
import xin.v5ai.nb.rag.KnowledgeDocument;
import xin.v5ai.nb.rag.KnowledgeManagementService;
import xin.v5ai.nb.rag.KnowledgeTask;
import xin.v5ai.nb.rag.TaskStatus;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeDocumentControllerTest {
    @Test
    void importsUrlAsDocument() {
        var documentRepository = new RecordingDocumentRepository();
        var service = new KnowledgeManagementService(
                new RecordingKnowledgeBaseRepository(),
                documentRepository,
                documentRepository,
                new RecordingTaskRepository(),
                new RecordingBindingRepository(),
                new RecordingVectorStore(),
                url -> "fetched content");
        var controller = new KnowledgeDocumentController(service);

        var document = controller.importUrl(1L, new ImportUrlRequest("https://example.com/doc", "External doc"));

        assertThat(document.fileType()).isEqualTo(DocumentFileType.URL);
        assertThat(document.source()).isEqualTo("https://example.com/doc");
        assertThat(document.status()).isEqualTo(DocumentStatus.PENDING);
    }

    @Test
    void retryDocumentResetsItsTasks() {
        var taskRepository = new RecordingTaskRepository();
        taskRepository.stored.add(new KnowledgeTask(7L, 1L, 10L, TaskStatus.FAILED, 3, 3, "boom"));
        var documentRepository = new RecordingDocumentRepository();
        var service = new KnowledgeManagementService(
                new RecordingKnowledgeBaseRepository(),
                documentRepository,
                documentRepository,
                taskRepository,
                new RecordingBindingRepository(),
                new RecordingVectorStore(),
                url -> "content");
        var controller = new KnowledgeDocumentController(service);

        controller.retryDocument(10L);

        assertThat(taskRepository.saved.getLast().status()).isEqualTo(TaskStatus.PENDING);
    }

    private static final class RecordingKnowledgeBaseRepository implements xin.v5ai.nb.rag.KnowledgeBaseRepository {
        @Override
        public KnowledgeBase save(KnowledgeBase knowledgeBase) {
            return new KnowledgeBase(1L, knowledgeBase.name(), knowledgeBase.description(), KnowledgeBaseStatus.ACTIVE);
        }

        @Override
        public KnowledgeBase findById(Long id) {
            return new KnowledgeBase(1L, "docs", null, KnowledgeBaseStatus.ACTIVE);
        }

        @Override
        public List<KnowledgeBase> list() {
            return List.of();
        }

        @Override
        public void disable(Long id) {
        }
    }

    private static final class RecordingDocumentRepository implements xin.v5ai.nb.rag.KnowledgeDocumentRepository,
            xin.v5ai.nb.rag.KnowledgeDocumentContentStore {
        @Override
        public KnowledgeDocument save(KnowledgeDocument document) {
            return new KnowledgeDocument(10L, document.knowledgeBaseId(), document.title(), document.fileType(),
                    document.source(), document.status(), document.errorMessage());
        }

        @Override
        public KnowledgeDocument findById(Long id) {
            return null;
        }

        @Override
        public List<KnowledgeDocument> findByKnowledgeBaseId(Long knowledgeBaseId) {
            return List.of();
        }

        @Override
        public void delete(Long id) {
        }

        @Override
        public byte[] loadContent(Long documentId) {
            return new byte[0];
        }

        @Override
        public void saveContent(Long documentId, byte[] content) {
        }
    }

    private static final class RecordingTaskRepository implements xin.v5ai.nb.rag.KnowledgeTaskRepository {
        private final List<KnowledgeTask> stored = new ArrayList<>();
        private final List<KnowledgeTask> saved = new ArrayList<>();

        @Override
        public KnowledgeTask save(KnowledgeTask task) {
            saved.add(task);
            return task;
        }

        @Override
        public KnowledgeTask findById(Long id) {
            return stored.stream().filter(task -> task.id().equals(id)).findFirst().orElse(null);
        }

        @Override
        public List<KnowledgeTask> findByDocumentId(Long documentId) {
            return stored.stream().filter(task -> task.documentId().equals(documentId)).toList();
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

    private static final class RecordingBindingRepository implements xin.v5ai.nb.rag.ApplicationKnowledgeBindingRepository {
        @Override
        public void save(String appKey, List<Long> knowledgeBaseIds) {
        }

        @Override
        public List<Long> findByAppKey(String appKey) {
            return List.of();
        }

        @Override
        public void delete(String appKey) {
        }
    }

    private static final class RecordingVectorStore implements xin.v5ai.nb.rag.VectorStore {
        @Override
        public void store(List<VectorChunk> chunks) {
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
