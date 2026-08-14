package xin.v5ai.nb.rag;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class KnowledgeManagementService {
    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeDocumentContentStore contentStore;
    private final KnowledgeTaskRepository taskRepository;
    private final ApplicationKnowledgeBindingRepository bindingRepository;
    private final VectorStore vectorStore;
    private final UrlContentFetcher urlContentFetcher;

    public KnowledgeManagementService(
            KnowledgeBaseRepository knowledgeBaseRepository,
            KnowledgeDocumentRepository documentRepository,
            KnowledgeDocumentContentStore contentStore,
            KnowledgeTaskRepository taskRepository,
            ApplicationKnowledgeBindingRepository bindingRepository,
            VectorStore vectorStore,
            UrlContentFetcher urlContentFetcher
    ) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentRepository = documentRepository;
        this.contentStore = contentStore;
        this.taskRepository = taskRepository;
        this.bindingRepository = bindingRepository;
        this.vectorStore = vectorStore;
        this.urlContentFetcher = urlContentFetcher;
    }

    public KnowledgeBase createKnowledgeBase(String name, String description) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("knowledge base name is required");
        }
        return knowledgeBaseRepository.save(new KnowledgeBase(name, description));
    }

    public List<KnowledgeBase> listKnowledgeBases() {
        return knowledgeBaseRepository.list();
    }

    public void disableKnowledgeBase(Long id) {
        requireKnowledgeBase(id);
        knowledgeBaseRepository.disable(id);
    }

    public KnowledgeDocument uploadDocument(Long knowledgeBaseId, String title, DocumentFileType fileType,
                                            byte[] content, String source) {
        requireKnowledgeBase(knowledgeBaseId);
        if (title == null || title.isBlank() || content == null || content.length == 0) {
            throw new IllegalArgumentException("document title and content are required");
        }
        var document = documentRepository.save(new KnowledgeDocument(null, knowledgeBaseId, title, fileType, source,
                DocumentStatus.PENDING, null));
        contentStore.saveContent(document.id(), content);
        submitIndexTask(document);
        return document;
    }

    public KnowledgeDocument importUrl(Long knowledgeBaseId, String title, String url) {
        requireKnowledgeBase(knowledgeBaseId);
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url is required");
        }
        var content = urlContentFetcher.fetch(url);
        return uploadDocument(knowledgeBaseId,
                title == null || title.isBlank() ? url : title,
                DocumentFileType.URL, content.getBytes(StandardCharsets.UTF_8), url);
    }

    public List<KnowledgeDocument> listDocuments(Long knowledgeBaseId) {
        requireKnowledgeBase(knowledgeBaseId);
        return documentRepository.findByKnowledgeBaseId(knowledgeBaseId);
    }

    public void deleteDocument(Long documentId) {
        var document = documentRepository.findById(documentId);
        if (document == null) {
            throw new IllegalArgumentException("document does not exist: " + documentId);
        }
        vectorStore.deleteByDocumentId(documentId);
        taskRepository.deleteByDocumentId(documentId);
        documentRepository.delete(documentId);
    }

    public void retryTask(Long taskId) {
        var task = taskRepository.findById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("task does not exist: " + taskId);
        }
        taskRepository.save(task.withStatus(TaskStatus.PENDING).withError(null));
    }

    public KnowledgeTask getTask(Long taskId) {
        var task = taskRepository.findById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("task does not exist: " + taskId);
        }
        return task;
    }

    public List<KnowledgeTask> listTasks(Long knowledgeBaseId) {
        return taskRepository.findByKnowledgeBaseId(knowledgeBaseId);
    }

    public List<KnowledgeTask> listTasksByDocument(Long documentId) {
        return taskRepository.findByDocumentId(documentId);
    }

    public void bindKnowledgeBases(String appKey, List<Long> knowledgeBaseIds) {
        if (knowledgeBaseIds == null) {
            throw new IllegalArgumentException("knowledgeBaseIds is required");
        }
        for (Long id : knowledgeBaseIds) {
            requireKnowledgeBase(id);
        }
        bindingRepository.save(appKey, knowledgeBaseIds);
    }

    public List<Long> getKnowledgeBindings(String appKey) {
        return bindingRepository.findByAppKey(appKey);
    }

    private void submitIndexTask(KnowledgeDocument document) {
        var task = new KnowledgeTask(null, document.knowledgeBaseId(), document.id(),
                TaskStatus.PENDING, 0, DEFAULT_MAX_ATTEMPTS, null);
        taskRepository.save(task);
    }

    private void requireKnowledgeBase(Long knowledgeBaseId) {
        if (knowledgeBaseId == null || knowledgeBaseRepository.findById(knowledgeBaseId) == null) {
            throw new IllegalArgumentException(
                    "knowledge base does not exist: " + knowledgeBaseId + legacyIdHint(knowledgeBaseId));
        }
    }

    /**
     * IDs above Number.MAX_SAFE_INTEGER cannot be represented exactly in JavaScript;
     * they originate from the pre-fix Snowflake id generation and cannot be
     * round-tripped by the web UI. Surface a hint instead of a confusing lookup miss.
     */
    private static String legacyIdHint(Long id) {
        return id != null && id > 9_007_199_254_740_991L
                ? " (该 ID 超过 JavaScript 安全整数范围，属于修复前的雪花 ID，前端无法精确回传；请在页面重新创建知识库)"
                : "";
    }
}
