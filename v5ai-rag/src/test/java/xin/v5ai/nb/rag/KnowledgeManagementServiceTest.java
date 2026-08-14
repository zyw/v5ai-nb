package xin.v5ai.nb.rag;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeManagementServiceTest {
    @Test
    void createsKnowledgeBaseAndAssignsId() {
        var repository = new RecordingKnowledgeBaseRepository();
        var service = service(repository);

        var knowledgeBase = service.createKnowledgeBase("Docs", "product docs");

        assertThat(knowledgeBase.id()).isEqualTo(1L);
        assertThat(knowledgeBase.status()).isEqualTo(KnowledgeBaseStatus.ACTIVE);
        assertThat(repository.saved).isSameAs(knowledgeBase);
    }

    @Test
    void uploadDocumentStoresContentAndSubmitsPendingTask() {
        var repository = new RecordingKnowledgeBaseRepository();
        var documentRepository = new RecordingKnowledgeDocumentRepository();
        var taskRepository = new RecordingKnowledgeTaskRepository();
        var service = service(repository, documentRepository, taskRepository);

        var document = service.uploadDocument(1L, "guide.md", DocumentFileType.MARKDOWN,
                "hello world".getBytes(java.nio.charset.StandardCharsets.UTF_8), "guide.md");

        assertThat(document.id()).isEqualTo(10L);
        assertThat(document.status()).isEqualTo(DocumentStatus.PENDING);
        assertThat(documentRepository.contentStore.get(10L)).isEqualTo("hello world".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(taskRepository.saved.getFirst().status()).isEqualTo(TaskStatus.PENDING);
        assertThat(taskRepository.saved.getFirst().documentId()).isEqualTo(10L);
        assertThat(taskRepository.saved.getFirst().maxAttempts()).isEqualTo(3);
    }

    @Test
    void uploadDocumentRejectsUnknownKnowledgeBase() {
        var service = service(new RecordingKnowledgeBaseRepository());

        assertThatThrownBy(() -> service.uploadDocument(99L, "a.txt", DocumentFileType.TXT, new byte[]{1}, "a.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("knowledge base does not exist");
    }

    @Test
    void bindKnowledgeBasesStoresOnlyExistingBases() {
        var repository = new RecordingKnowledgeBaseRepository();
        var bindingRepository = new RecordingBindingRepository();
        var service = service(repository, bindingRepository);

        service.bindKnowledgeBases("demo", List.of(1L));

        assertThat(bindingRepository.bound.get("demo")).containsExactly(1L);
    }

    @Test
    void bindKnowledgeBasesRejectsMissingBase() {
        var bindingRepository = new RecordingBindingRepository();
        var service = service(new RecordingKnowledgeBaseRepository(), bindingRepository);

        assertThatThrownBy(() -> service.bindKnowledgeBases("demo", List.of(99L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void retryTaskResetsStatusToPendingAndClearsError() {
        var taskRepository = new RecordingKnowledgeTaskRepository();
        taskRepository.stored = new KnowledgeTask(7L, 1L, 10L, TaskStatus.FAILED, 3, 3, "boom");
        var service = service(new RecordingKnowledgeBaseRepository(), new RecordingKnowledgeDocumentRepository(), taskRepository);

        service.retryTask(7L);

        assertThat(taskRepository.saved.getFirst().status()).isEqualTo(TaskStatus.PENDING);
        assertThat(taskRepository.saved.getFirst().errorMessage()).isNull();
    }

    @Test
    void deleteDocumentRemovesChunksTasksAndRow() {
        var documentRepository = new RecordingKnowledgeDocumentRepository();
        documentRepository.stored.put(10L, new KnowledgeDocument(10L, 1L, "guide.md", DocumentFileType.MARKDOWN,
                "guide.md", DocumentStatus.COMPLETED, null));
        var taskRepository = new RecordingKnowledgeTaskRepository();
        var vectorStore = new RecordingVectorStore();
        var service = service(new RecordingKnowledgeBaseRepository(), documentRepository, taskRepository, vectorStore);

        service.deleteDocument(10L);

        assertThat(vectorStore.deletedDocumentId).isEqualTo(10L);
        assertThat(taskRepository.deletedDocumentId).isEqualTo(10L);
        assertThat(documentRepository.deletedId).isEqualTo(10L);
    }

    private static KnowledgeManagementService service(RecordingKnowledgeBaseRepository repository) {
        var documentRepository = new RecordingKnowledgeDocumentRepository();
        return new KnowledgeManagementService(repository, documentRepository, documentRepository,
                new RecordingKnowledgeTaskRepository(), new RecordingBindingRepository(),
                new RecordingVectorStore(), url -> "content");
    }

    private static KnowledgeManagementService service(RecordingKnowledgeBaseRepository repository,
                                                      RecordingKnowledgeDocumentRepository documentRepository,
                                                      RecordingKnowledgeTaskRepository taskRepository) {
        return new KnowledgeManagementService(repository, documentRepository, documentRepository,
                taskRepository, new RecordingBindingRepository(), new RecordingVectorStore(), url -> "content");
    }

    private static KnowledgeManagementService service(RecordingKnowledgeBaseRepository repository,
                                                      RecordingBindingRepository bindingRepository) {
        var documentRepository = new RecordingKnowledgeDocumentRepository();
        return new KnowledgeManagementService(repository, documentRepository, documentRepository,
                new RecordingKnowledgeTaskRepository(), bindingRepository, new RecordingVectorStore(), url -> "content");
    }

    private static KnowledgeManagementService service(RecordingKnowledgeBaseRepository repository,
                                                      RecordingKnowledgeDocumentRepository documentRepository,
                                                      RecordingKnowledgeTaskRepository taskRepository,
                                                      RecordingVectorStore vectorStore) {
        return new KnowledgeManagementService(repository, documentRepository, documentRepository,
                taskRepository, new RecordingBindingRepository(), vectorStore, url -> "content");
    }

    private static final class RecordingKnowledgeBaseRepository implements KnowledgeBaseRepository {
        private final List<KnowledgeBase> stored = new ArrayList<>();
        private KnowledgeBase saved;

        @Override
        public KnowledgeBase save(KnowledgeBase knowledgeBase) {
            var withId = new KnowledgeBase(1L, knowledgeBase.name(), knowledgeBase.description(), knowledgeBase.status());
            saved = withId;
            stored.add(withId);
            return withId;
        }

        @Override
        public KnowledgeBase findById(Long id) {
            if (Long.valueOf(1L).equals(id)) {
                var known = stored.stream().filter(base -> Long.valueOf(1L).equals(base.id())).findFirst().orElse(null);
                return known != null ? known : new KnowledgeBase(1L, "docs", null, KnowledgeBaseStatus.ACTIVE);
            }
            return null;
        }

        @Override
        public List<KnowledgeBase> list() {
            return stored;
        }

        @Override
        public void disable(Long id) {
        }
    }

    private static final class RecordingKnowledgeDocumentRepository implements KnowledgeDocumentRepository,
            KnowledgeDocumentContentStore {
        private final java.util.Map<Long, KnowledgeDocument> stored = new java.util.HashMap<>();
        private final java.util.Map<Long, byte[]> contentStore = new java.util.HashMap<>();
        private Long deletedId;

        @Override
        public KnowledgeDocument save(KnowledgeDocument document) {
            var withId = document.id() == null
                    ? new KnowledgeDocument(10L, document.knowledgeBaseId(), document.title(), document.fileType(),
                    document.source(), document.status(), document.errorMessage())
                    : document;
            stored.put(withId.id(), withId);
            return withId;
        }

        @Override
        public KnowledgeDocument findById(Long id) {
            return stored.get(id);
        }

        @Override
        public List<KnowledgeDocument> findByKnowledgeBaseId(Long knowledgeBaseId) {
            return stored.values().stream().filter(document -> document.knowledgeBaseId().equals(knowledgeBaseId)).toList();
        }

        @Override
        public void delete(Long id) {
            deletedId = id;
            stored.remove(id);
        }

        @Override
        public byte[] loadContent(Long documentId) {
            return contentStore.getOrDefault(documentId, new byte[0]);
        }

        @Override
        public void saveContent(Long documentId, byte[] content) {
            contentStore.put(documentId, content);
        }
    }

    private static final class RecordingKnowledgeTaskRepository implements KnowledgeTaskRepository {
        private final List<KnowledgeTask> saved = new ArrayList<>();
        private KnowledgeTask stored;
        private Long deletedDocumentId;

        @Override
        public KnowledgeTask save(KnowledgeTask task) {
            var withId = task.id() == null
                    ? new KnowledgeTask(7L, task.knowledgeBaseId(), task.documentId(), task.status(),
                    task.attemptCount(), task.maxAttempts(), task.errorMessage())
                    : task;
            saved.add(withId);
            stored = withId;
            return withId;
        }

        @Override
        public KnowledgeTask findById(Long id) {
            return stored;
        }

        @Override
        public List<KnowledgeTask> findByDocumentId(Long documentId) {
            return saved.stream().filter(task -> task.documentId().equals(documentId)).toList();
        }

        @Override
        public List<KnowledgeTask> findByKnowledgeBaseId(Long knowledgeBaseId) {
            return saved.stream().filter(task -> task.knowledgeBaseId().equals(knowledgeBaseId)).toList();
        }

        @Override
        public void deleteByDocumentId(Long documentId) {
            deletedDocumentId = documentId;
        }

        @Override
        public List<KnowledgeTask> findProcessable(int limit) {
            return saved.stream().filter(task -> task.status() == TaskStatus.PENDING).toList();
        }
    }

    private static final class RecordingBindingRepository implements ApplicationKnowledgeBindingRepository {
        private final java.util.Map<String, List<Long>> bound = new java.util.HashMap<>();

        @Override
        public void save(String appKey, List<Long> knowledgeBaseIds) {
            bound.put(appKey, knowledgeBaseIds);
        }

        @Override
        public List<Long> findByAppKey(String appKey) {
            return bound.getOrDefault(appKey, List.of());
        }

        @Override
        public void delete(String appKey) {
            bound.remove(appKey);
        }
    }

    private static final class RecordingVectorStore implements VectorStore {
        private Long deletedDocumentId;

        @Override
        public void store(List<VectorChunk> chunks) {
        }

        @Override
        public void deleteByDocumentId(Long documentId) {
            deletedDocumentId = documentId;
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
