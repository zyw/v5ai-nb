package xin.v5ai.nb.worker;

import org.springframework.stereotype.Service;
import xin.v5ai.nb.rag.DocumentParser;
import xin.v5ai.nb.rag.DocumentStatus;
import xin.v5ai.nb.rag.DocumentChunker;
import xin.v5ai.nb.rag.EmbeddingClient;
import xin.v5ai.nb.rag.KnowledgeDocument;
import xin.v5ai.nb.rag.KnowledgeDocumentContentStore;
import xin.v5ai.nb.rag.KnowledgeDocumentRepository;
import xin.v5ai.nb.rag.KnowledgeTask;
import xin.v5ai.nb.rag.KnowledgeTaskRepository;
import xin.v5ai.nb.rag.TaskStatus;
import xin.v5ai.nb.rag.VectorStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Executes the document indexing pipeline for one task:
 * parse → chunk → embed → store vectors, with PENDING/PROCESSING/COMPLETED/FAILED
 * state transitions and retry accounting.
 */
@Service
public class KnowledgeIndexingService {
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
        var document = documentRepository.findById(task.documentId());
        if (document == null) {
            return;
        }
        var runningTask = task.withAttempt().withStatus(TaskStatus.PROCESSING).withError(null);
        taskRepository.save(runningTask);
        documentRepository.save(document.withStatus(DocumentStatus.PROCESSING));
        try {
            var content = contentStore.loadContent(document.id());
            var text = documentParser.parse(content, document.fileType());
            var chunks = documentChunker.chunk(text);
            var vectors = new ArrayList<VectorStore.VectorChunk>(chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                vectors.add(new VectorStore.VectorChunk(
                        document.knowledgeBaseId(),
                        document.id(),
                        i,
                        chunks.get(i),
                        embeddingClient.embed(chunks.get(i)),
                        metadata(document, i)));
            }
            vectorStore.deleteByDocumentId(document.id());
            vectorStore.store(vectors);
            documentRepository.save(document.withStatus(DocumentStatus.COMPLETED));
            taskRepository.save(runningTask.withStatus(TaskStatus.COMPLETED));
        } catch (Exception exception) {
            var errorMessage = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            taskRepository.save(runningTask.withStatus(TaskStatus.FAILED).withError(errorMessage));
            documentRepository.save(document.withStatus(DocumentStatus.FAILED).withError(errorMessage));
        }
    }

    private String metadata(KnowledgeDocument document, int chunkIndex) {
        return "{\"documentId\":%d,\"chunkIndex\":%d,\"title\":\"%s\"}".formatted(
                document.id(), chunkIndex, escape(document.title()));
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\"", "\\\"").replace("\n", " ");
    }
}
