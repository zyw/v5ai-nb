package xin.v5ai.nb.api.rag;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.api.common.OptionResponse;
import xin.v5ai.nb.common.core.page.PageQuery;
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

class KnowledgeBaseControllerTest {
    @Test
    void createsAndListsKnowledgeBases() {
        var service = service();
        var controller = new KnowledgeBaseController(service);

        var created = controller.createKnowledgeBase(new CreateKnowledgeBaseRequest("Docs", "product docs")).getData();
        var listed = controller.listKnowledgeBases(new PageQuery()).getData().getRows();

        assertThat(created.id()).isEqualTo(1L);
        assertThat(created.status()).isEqualTo(KnowledgeBaseStatus.ACTIVE);
        assertThat(listed).extracting(KnowledgeBaseResponse::name).containsExactly("Docs");
    }

    @Test
    void listsKnowledgeBaseOptions() {
        var service = service();
        var controller = new KnowledgeBaseController(service);
        controller.createKnowledgeBase(new CreateKnowledgeBaseRequest("Docs", "product docs"));

        var options = controller.listKnowledgeBaseOptions().getData();

        assertThat(options).extracting(OptionResponse::value).containsExactly(1L);
        assertThat(options).extracting(OptionResponse::label).containsExactly("Docs (#1)");
    }

    @Test
    void listsTasksForKnowledgeBase() {
        var service = service();
        service.uploadDocument(1L, "guide.md", DocumentFileType.MARKDOWN, "content".getBytes(), "guide.md");
        var controller = new KnowledgeBaseController(service);

        var tasks = controller.listTasks(1L, new PageQuery()).getData().getRows();

        assertThat(tasks).extracting(TaskResponse::status).containsExactly(TaskStatus.PENDING);
    }

    private static KnowledgeManagementService service() {
        return new KnowledgeManagementService(
                new RecordingKnowledgeBaseRepository(),
                new RecordingDocumentRepository(),
                new RecordingDocumentRepository(),
                new RecordingTaskRepository(),
                new RecordingBindingRepository(),
                new RecordingVectorStore(),
                url -> "content");
    }

    private static final class RecordingKnowledgeBaseRepository implements xin.v5ai.nb.rag.KnowledgeBaseRepository {
        private final List<KnowledgeBase> stored = new ArrayList<>();

        @Override
        public KnowledgeBase save(KnowledgeBase knowledgeBase) {
            var withId = new KnowledgeBase(1L, knowledgeBase.name(), knowledgeBase.description(), knowledgeBase.status());
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

        @Override
        public KnowledgeTask save(KnowledgeTask task) {
            stored.add(new KnowledgeTask(7L, task.knowledgeBaseId(), task.documentId(), task.status(),
                    task.attemptCount(), task.maxAttempts(), task.errorMessage()));
            return stored.getLast();
        }

        @Override
        public KnowledgeTask findById(Long id) {
            return stored.stream().filter(task -> task.id().equals(id)).findFirst().orElse(null);
        }

        @Override
        public List<KnowledgeTask> findByDocumentId(Long documentId) {
            return List.of();
        }

        @Override
        public List<KnowledgeTask> findByKnowledgeBaseId(Long knowledgeBaseId) {
            return stored.stream().filter(task -> task.knowledgeBaseId().equals(knowledgeBaseId)).toList();
        }

        @Override
        public void deleteByDocumentId(Long documentId) {
        }

        @Override
        public List<KnowledgeTask> findProcessable(int limit) {
            return List.of();
        }
    }

    private static final class RecordingBindingRepository implements xin.v5ai.nb.rag.AgentKnowledgeBindingRepository {
        @Override
        public void save(String agentKey, List<Long> knowledgeBaseIds) {
        }

        @Override
        public List<Long> findByAgentKey(String agentKey) {
            return List.of();
        }

        @Override
        public void delete(String agentKey) {
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
