package xin.v5ai.nb.worker;

import org.springframework.stereotype.Service;
import xin.v5ai.nb.rag.DocumentFileType;
import xin.v5ai.nb.rag.DocumentParser;
import xin.v5ai.nb.rag.DocumentChunker;
import xin.v5ai.nb.rag.EmbeddingClient;
import xin.v5ai.nb.rag.KnowledgeDocumentContentStore;
import xin.v5ai.nb.rag.KnowledgeDocumentRepository;
import xin.v5ai.nb.rag.KnowledgeTaskRepository;
import xin.v5ai.nb.rag.VectorStore;
import xin.v5ai.nb.rag.domain.KnowledgeDocument;

import java.util.ArrayList;
import java.util.List;

/**
 * Executes the document indexing pipeline for one task:
 * parse → chunk → embed → store vectors, with PENDING/PROCESSING/COMPLETED/FAILED
 * state transitions and retry accounting.
 */
@Service
public class KnowledgeIndexingService {
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";

    private final KnowledgeTaskRepository taskRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeDocumentContentStore contentStore;
    private final DocumentParser documentParser;
    private final DocumentChunker documentChunker;
    private final EmbeddingClient embeddingClient;
    private final VectorStore vectorStore;

    public KnowledgeIndexingService(
            KnowledgeTaskRepository taskRepository,
            KnowledgeDocumentRepository documentRepository,
            KnowledgeDocumentContentStore contentStore,
            DocumentParser documentParser,
            DocumentChunker documentChunker,
            EmbeddingClient embeddingClient,
            VectorStore vectorStore
    ) {
        this.taskRepository = taskRepository;
        this.documentRepository = documentRepository;
        this.contentStore = contentStore;
        this.documentParser = documentParser;
        this.documentChunker = documentChunker;
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
    }

    public void processTask(Long taskId) {
        var task = taskRepository.findById(taskId);
        if (task == null) {
            return;
        }
        var document = documentRepository.findById(task.getDocumentId());
        if (document == null) {
            return;
        }
        task.setAttemptCount(task.getAttemptCount() + 1);
        task.setStatus(STATUS_PROCESSING);
        task.setErrorMessage(null);
        taskRepository.save(task);
        document.setStatus(STATUS_PROCESSING);
        documentRepository.save(document);
        try {
            var content = contentStore.loadContent(document.getId());
            var text = documentParser.parse(content, DocumentFileType.valueOf(document.getFileType()));
            var chunks = documentChunker.chunk(text);
            var vectors = new ArrayList<VectorStore.VectorChunk>(chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                vectors.add(new VectorStore.VectorChunk(
                        document.getKnowledgeBaseId(),
                        document.getId(),
                        i,
                        chunks.get(i),
                        embeddingClient.embed(chunks.get(i)),
                        metadata(document, i)));
            }
            vectorStore.deleteByDocumentId(document.getId());
            vectorStore.store(vectors);
            document.setStatus(STATUS_COMPLETED);
            documentRepository.save(document);
            task.setStatus(STATUS_COMPLETED);
            taskRepository.save(task);
        } catch (Exception exception) {
            var errorMessage = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            task.setStatus(STATUS_FAILED);
            task.setErrorMessage(errorMessage);
            taskRepository.save(task);
            document.setStatus(STATUS_FAILED);
            document.setErrorMessage(errorMessage);
            documentRepository.save(document);
        }
    }

    private String metadata(KnowledgeDocument document, int chunkIndex) {
        return "{\"documentId\":%d,\"chunkIndex\":%d,\"title\":\"%s\"}".formatted(
                document.getId(), chunkIndex, escape(document.getTitle()));
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\"", "\\\"").replace("\n", " ");
    }
}
