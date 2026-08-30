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
 * parse → chunk → embed → store vectors, with 0-待处理/1-解析中/2-处理中/3-处理完成/4-处理失败
 * document state transitions and retry accounting (任务表状态仍为 PENDING/PROCESSING/
 * COMPLETED/FAILED 字符串)。切片按知识库 config.chunkParams 与 delimiter 列执行；
 * 未配置时回退到全局默认参数。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeIndexingService {

    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";

    /**
     * 文档状态: 1-解析中
     */
    private static final int DOC_STATUS_PARSING = 1;

    /**
     * 文档状态: 2-处理中
     */
    private static final int DOC_STATUS_PROCESSING = 2;

    /**
     * 文档状态: 3-处理完成
     */
    private static final int DOC_STATUS_COMPLETED = 3;

    /**
     * 文档状态: 4-处理失败
     */
    private static final int DOC_STATUS_FAILED = 4;

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
        document.setStatus(DOC_STATUS_PARSING);
        documentRepository.save(document);
        try {
            var content = contentStore.loadContent(document.getId());
            var parseStart = System.currentTimeMillis();
            var text = documentParser.parse(content, DocumentFileType.valueOf(document.getFileType()));
            var parseTime = (int) (System.currentTimeMillis() - parseStart);
            var chunks = documentChunker.chunk(text, chunkingOptions(document.getKnowledgeBaseId()));
            document.setStatus(DOC_STATUS_PROCESSING);
            document.setChunkCount(chunks.size());
            documentRepository.save(document);
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
            document.setParseTime(parseTime);
            document.setStatus(DOC_STATUS_COMPLETED);
            documentRepository.save(document);
            task.setStatus(STATUS_COMPLETED);
            taskRepository.save(task);
        } catch (Exception exception) {
            var errorMessage = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            task.setStatus(STATUS_FAILED);
            task.setErrorMessage(errorMessage);
            taskRepository.save(task);
            document.setStatus(DOC_STATUS_FAILED);
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
