package xin.v5ai.nb.worker;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.common.core.utils.ObjectUtils;
import xin.v5ai.nb.rag.core.ChunkMode;
import xin.v5ai.nb.rag.core.ChunkingOptions;
import xin.v5ai.nb.rag.core.chunker.DocumentChunker;
import xin.v5ai.nb.rag.core.EmbeddingClient;
import xin.v5ai.nb.rag.core.KnowledgeDocumentContentStore;
import xin.v5ai.nb.rag.core.config.RagConfigDO;
import xin.v5ai.nb.rag.core.enums.DocumentFileType;
import xin.v5ai.nb.rag.core.parser.DocumentParseCoordinator;
import xin.v5ai.nb.rag.core.parser.ParseRequest;
import xin.v5ai.nb.rag.core.store.KeywordStore;
import xin.v5ai.nb.rag.core.store.VectorStore;
import xin.v5ai.nb.rag.core.store.VectorStoreResolver;
import xin.v5ai.nb.rag.domain.KnowledgeDocument;
import xin.v5ai.nb.rag.domain.vo.KnowledgeBaseVo;
import xin.v5ai.nb.rag.service.IKnowledgeBaseService;
import xin.v5ai.nb.rag.service.IKnowledgeDocumentService;
import xin.v5ai.nb.rag.service.IKnowledgeTaskService;

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

    /**
     * chunk 来源类型: 文本
     */
    private static final String SOURCE_TYPE_TEXT = "TEXT";

    private final IKnowledgeTaskService taskService;
    private final IKnowledgeDocumentService documentService;
    private final IKnowledgeBaseService knowledgeBaseService;
    private final KnowledgeDocumentContentStore contentStore;
    private final DocumentParseCoordinator documentParseCoordinator;
    private final DocumentChunker documentChunker;
    private final EmbeddingClient embeddingClient;
    private final VectorStoreResolver vectorStoreResolver;
    private final xin.v5ai.nb.rag.service.IKnowledgeChunkService chunkService;

    public void processTask(Long taskId) {
        var task = taskService.findById(taskId);
        if (task == null) {
            return;
        }
        var document = documentService.findById(task.getDocumentId());
        if (document == null) {
            return;
        }
        task.setAttemptCount(task.getAttemptCount() + 1);
        task.setStatus(STATUS_PROCESSING);
        task.setErrorMessage(null);
        taskService.save(task);
        document.setStatus(DOC_STATUS_PARSING);
        documentService.save(document);
        try {
            var content = contentStore.loadContent(document.getId());
            var knowledgeBase = knowledgeBaseService.getKnowledgeBase(document.getKnowledgeBaseId());
            if (knowledgeBase == null || knowledgeBase.getVectorStoreInstanceId() == null) {
                throw new IllegalStateException("知识库未绑定向量存储实例，无法索引: kb=" + document.getKnowledgeBaseId());
            }
            var parseStart = System.currentTimeMillis();
            var config = knowledgeBase.getConfig();
            var parseParams = config == null ? null : config.getParseParams();
            var parsed = documentParseCoordinator.parse(new ParseRequest(
                    content,
                    document.getTitle(),
                    DocumentFileType.valueOf(document.getFileType()),
                    document.getId(),
                    document.getKnowledgeBaseId(),
                    parseParams));
            var text = parsed.content();
            var parseTime = (int) (System.currentTimeMillis() - parseStart);
            document.setParsedText(text);
            document.setParseEngine(parsed.getEngine());
            document.setParseDiagnostics(parsed.getStructuredJson());
            document.setParseTime(parseTime);
            documentService.save(document);
            var chunks = documentChunker.chunk(text, chunkingOptions(knowledgeBase));
            document.setStatus(DOC_STATUS_PROCESSING);
            document.setChunkCount(chunks.size());
            documentService.save(document);
            var vectors = new ArrayList<VectorStore.VectorChunk>(chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                vectors.add(new VectorStore.VectorChunk(
                        IdUtil.simpleUUID(),
                        document.getKnowledgeBaseId(),
                        document.getId(),
                        i,
                        chunks.get(i),
                        embeddingClient.embed(chunks.get(i), knowledgeBase.getEmbeddingModelId(),
                                knowledgeBase.getDimensionOfVectorModel()),
                        metadata(document, i),
                        tokenCount(chunks.get(i)),
                        sha256Hex(chunks.get(i)),
                        SOURCE_TYPE_TEXT));
            }
            var vectorStore = vectorStoreResolver.vectorStore(knowledgeBase.getVectorStoreInstanceId());
            // 幂等重建：先清旧业务行与旧向量，再写新业务行（含 vector_id）与新向量
            chunkService.deleteByDocumentId(document.getKnowledgeBaseId(), document.getId());
            vectorStore.deleteByDocumentId(document.getKnowledgeBaseId(), document.getId());
            var businessRows = new ArrayList<xin.v5ai.nb.rag.domain.KnowledgeChunk>(vectors.size());
            for (var v : vectors) {
                var row = new xin.v5ai.nb.rag.domain.KnowledgeChunk();
                row.setKnowledgeBaseId(v.knowledgeBaseId());
                row.setDocumentId(v.documentId());
                row.setChunkIndex(v.chunkIndex());
                row.setContent(v.content());
                row.setVectorId(v.vectorId());
                row.setTokenCount(v.tokenCount());
                row.setContentHash(v.contentHash());
                row.setSourceType(v.sourceType());
                row.setMetadata(ObjectUtils.defaultIfNull(v.metadata(), "{}"));
                businessRows.add(row);
            }
            chunkService.saveBatch(businessRows);
            vectorStore.store(vectors);
            // 双写：同时启用搜索引擎且与向量库不同实例时，将切片写入搜索引擎以支持关键词召回
            var keywordStore = dualWriteKeywordStore(knowledgeBase);
            if (keywordStore != null) {
                keywordStore.deleteByDocumentId(document.getKnowledgeBaseId(), document.getId());
                keywordStore.store(vectors);
            }
            document.setStatus(DOC_STATUS_COMPLETED);
            documentService.save(document);
            task.setStatus(STATUS_COMPLETED);
            taskService.save(task);
        } catch (Exception exception) {
            var errorMessage = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            task.setStatus(STATUS_FAILED);
            task.setErrorMessage(errorMessage);
            taskService.save(task);
            document.setStatus(DOC_STATUS_FAILED);
            document.setErrorMessage(errorMessage);
            documentService.save(document);
        }
    }

    /**
     * 双写条件：开启搜索引擎 + 配置了搜索引擎实例 + 与向量库实例不同。
     */
    private KeywordStore dualWriteKeywordStore(KnowledgeBaseVo knowledgeBase) {
        if (knowledgeBase == null || !Boolean.TRUE.equals(knowledgeBase.getSearchEngineEnable())) {
            return null;
        }
        Long searchEngineId = knowledgeBase.getSearchEngineInstanceId();
        if (searchEngineId == null || searchEngineId.equals(knowledgeBase.getVectorStoreInstanceId())) {
            return null;
        }
        return vectorStoreResolver.keywordStore(searchEngineId);
    }

    /**
     * 按知识库配置构建切片参数；delimiter 模式未配置自定义分隔符时回退到知识库 delimiter 列。
     */
    private ChunkingOptions chunkingOptions(KnowledgeBaseVo base) {
        if (base == null) {
            return ChunkingOptions.DEFAULT;
        }
//        RagConfigDO config = RagConfigDO.fromJson(base.getConfig());
        RagConfigDO config =base.getConfig();
        var chunkParams = config == null ? null : config.getChunkParams();
        if (chunkParams == null) {
            return new ChunkingOptions(null, null, null, base.getDelimiter(), null, false);
        }
        // 分隔符可能是纯空白（如 \n\n），不能用 isBlank 判空
        String customDelimiter = StrUtil.isEmpty(chunkParams.getCustomDelimiter())
                ? base.getDelimiter() : chunkParams.getCustomDelimiter();
        return new ChunkingOptions(ChunkMode.from(chunkParams.getSliceStrategy()),
                chunkParams.getMaxChunkLength(), chunkParams.getChunkOverlap(),
                customDelimiter, chunkParams.getChunkRegex(),
                Boolean.TRUE.equals(chunkParams.getMergeShortSegments()),
                chunkParams.getChunkModelId());
    }

    private String metadata(KnowledgeDocument document, int chunkIndex) {
        return "{\"documentId\":%d,\"chunkIndex\":%d,\"title\":\"%s\"}".formatted(
                document.getId(), chunkIndex, escape(document.getTitle()));
    }

    /**
     * 按空白切分估算 chunk 的 token 数量。
     */
    private static int tokenCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.split("\\s+").length;
    }

    /**
     * 计算 chunk 内容 SHA-256 十六进制摘要（用于向量去重）。
     */
    private static String sha256Hex(String text) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\"", "\\\"").replace("\n", " ");
    }
}
