package xin.v5ai.nb.worker;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.rag.core.DocumentChunker;
import xin.v5ai.nb.rag.core.EmbeddingClient;
import xin.v5ai.nb.rag.core.KnowledgeDocumentContentStore;
import xin.v5ai.nb.rag.core.enums.DocumentFileType;
import xin.v5ai.nb.rag.core.parser.DocumentParser;
import xin.v5ai.nb.rag.core.repository.KnowledgeDocumentRepository;
import xin.v5ai.nb.rag.core.repository.KnowledgeTaskRepository;
import xin.v5ai.nb.rag.core.store.VectorStore;
import xin.v5ai.nb.rag.domain.KnowledgeDocument;

import java.util.ArrayList;

/**
 * Executes the document indexing pipeline for one task:
 * parse → chunk → embed → store vectors, with PENDING/PROCESSING/COMPLETED/FAILED
 * state transitions and retry accounting.
 */
@Service
@RequiredArgsConstructor
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
