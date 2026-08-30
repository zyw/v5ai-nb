package xin.v5ai.nb.worker;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.rag.core.ChunkingOptions;
import xin.v5ai.nb.rag.core.DocumentChunker;
import xin.v5ai.nb.rag.core.EmbeddingClient;
import xin.v5ai.nb.rag.core.KnowledgeDocumentContentStore;
import xin.v5ai.nb.rag.core.config.RagConfigDO;
import xin.v5ai.nb.rag.core.enums.DocumentFileType;
import xin.v5ai.nb.rag.core.parser.DocumentParser;
import xin.v5ai.nb.rag.core.repository.KnowledgeBaseRepository;
import xin.v5ai.nb.rag.core.repository.KnowledgeDocumentRepository;
import xin.v5ai.nb.rag.core.repository.KnowledgeTaskRepository;
import xin.v5ai.nb.rag.core.store.VectorStore;
import xin.v5ai.nb.rag.domain.KnowledgeDocument;

import java.util.ArrayList;

/**
 * Executes the document indexing pipeline for one task:
 * parse → chunk → embed → store vectors, with PENDING/PROCESSING/COMPLETED/FAILED
 * state transitions and retry accounting. 切片按知识库 config.chunkParams 与
 * delimiter 列执行；未配置时回退到全局默认参数。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeIndexingService {
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";

    private final KnowledgeTaskRepository taskRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
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
            var chunks = documentChunker.chunk(text, chunkingOptions(document.getKnowledgeBaseId()));
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

    /**
     * 按知识库配置构建切片参数；delimiter 模式未配置自定义分隔符时回退到知识库 delimiter 列。
     */
    private ChunkingOptions chunkingOptions(Long knowledgeBaseId) {
        var base = knowledgeBaseRepository.findById(knowledgeBaseId);
        if (base == null) {
            return ChunkingOptions.DEFAULT;
        }
        RagConfigDO config = RagConfigDO.fromJson(base.getConfig());
        var chunkParams = config == null ? null : config.getChunkParams();
        if (chunkParams == null) {
            return new ChunkingOptions(null, null, null, base.getDelimiter(), null, false);
        }
        // 分隔符可能是纯空白（如 \n\n），不能用 isBlank 判空
        String customDelimiter = StrUtil.isEmpty(chunkParams.getCustomDelimiter())
                ? base.getDelimiter() : chunkParams.getCustomDelimiter();
        return new ChunkingOptions(chunkParams.getMode(), chunkParams.getMaxChunkTokens(),
                chunkParams.getChunkOverlap(), customDelimiter, chunkParams.getChunkRegex(),
                Boolean.TRUE.equals(chunkParams.getMergeShortSegments()));
    }

    private String metadata(KnowledgeDocument document, int chunkIndex) {
        return "{\"documentId\":%d,\"chunkIndex\":%d,\"title\":\"%s\"}".formatted(
                document.getId(), chunkIndex, escape(document.getTitle()));
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\"", "\\\"").replace("\n", " ");
    }
}
