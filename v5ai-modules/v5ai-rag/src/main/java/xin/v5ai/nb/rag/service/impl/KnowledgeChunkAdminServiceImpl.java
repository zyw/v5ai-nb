package xin.v5ai.nb.rag.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.core.exception.ServiceException;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.rag.core.EmbeddingClient;
import xin.v5ai.nb.rag.core.store.VectorStore;
import xin.v5ai.nb.rag.core.store.VectorStoreResolver;
import xin.v5ai.nb.rag.core.tokenizer.TextTokenizer;
import xin.v5ai.nb.rag.domain.KnowledgeBase;
import xin.v5ai.nb.rag.domain.KnowledgeChunk;
import xin.v5ai.nb.rag.domain.KnowledgeDocument;
import xin.v5ai.nb.rag.domain.bo.KbChunkAddBo;
import xin.v5ai.nb.rag.domain.bo.KbChunkEditBo;
import xin.v5ai.nb.rag.domain.vo.KnowledgeChunkVo;
import xin.v5ai.nb.rag.mapper.KnowledgeBaseMapper;
import xin.v5ai.nb.rag.mapper.KnowledgeChunkMapper;
import xin.v5ai.nb.rag.mapper.KnowledgeDocumentMapper;
import xin.v5ai.nb.rag.service.IKnowledgeChunkAdminService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识库切片管理实现：浏览/统计走切片业务表（不含向量），写操作与 Worker 同链路。
 * <p>
 * 手工切片的向量存储走 {@link VectorStore} 端口：业务行（content/metadata/vector_id）先落
 * {@code v5ai_knowledge_chunk}，向量再写入知识库绑定的存储实例集合（主键 = vector_id）。
 * 单切片编辑/删除同步维护业务行与集合内向量。
 * <p>
 * 写操作要求所属文档已进入索引终态（否则 Worker 重建会静默覆盖），并同步维护所属文档的分片数量。
 *
 * @author ZYW
 * @since 2026-09-06
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeChunkAdminServiceImpl implements IKnowledgeChunkAdminService {

    /**
     * 手工切片内容最大长度（字符）
     */
    private static final int MAX_CHUNK_CHARS = 200_000;

    /**
     * 手工切片元数据标记
     */
    private static final String MANUAL_METADATA = "{\"source\":\"manual\"}";

    /**
     * 文档索引终态边界：3-处理完成 / 4-处理失败（0-待处理 1-解析中 2-处理中）
     */
    private static final int DOC_STATUS_COMPLETED = 3;

    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final EmbeddingClient embeddingClient;
    private final VectorStoreResolver vectorStoreResolver;
    private final TextTokenizer textTokenizer;

    @Override
    public PageResult<KnowledgeChunkVo> listChunks(Long knowledgeBaseId, Long documentId, Long chunkId,
                                                   String content, PageQuery pageQuery) {
        requireKnowledgeBase(knowledgeBaseId);
        LambdaQueryWrapper<KnowledgeChunk> wrapper = new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getKnowledgeBaseId, knowledgeBaseId)
                .eq(documentId != null, KnowledgeChunk::getDocumentId, documentId)
                .eq(chunkId != null, KnowledgeChunk::getId, chunkId)
                .like(StrUtil.isNotBlank(content), KnowledgeChunk::getContent, content)
                .orderByDesc(KnowledgeChunk::getId);
        Page<KnowledgeChunk> page = pageQuery.build();
        List<KnowledgeChunk> chunks = chunkMapper.selectList(page, wrapper);
        Map<Long, String> titles = documentTitles(chunks.stream().map(KnowledgeChunk::getDocumentId).toList());
        List<KnowledgeChunkVo> vos = chunks.stream()
                .map(chunk -> toVo(chunk, titles.get(chunk.getDocumentId())))
                .toList();
        return PageResult.build(vos, page.getTotal());
    }

    @Override
    public long countByKnowledgeBaseId(Long knowledgeBaseId) {
        return chunkMapper.selectCount(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getKnowledgeBaseId, knowledgeBaseId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeChunkVo addChunk(Long knowledgeBaseId, KbChunkAddBo bo) {
        if (bo == null || bo.documentId() == null) {
            throw new ServiceException("请选择所属文档");
        }
        String content = requireContent(bo.content());
        KnowledgeDocument document = requireDocumentOfBase(knowledgeBaseId, bo.documentId());
        requireDocumentIndexSettled(document);
        KnowledgeBase base = requireKnowledgeBase(knowledgeBaseId);

        String vectorId = java.util.UUID.randomUUID().toString();
        int nextIndex = chunkMapper.nextChunkIndex(knowledgeBaseId, document.getId());
        List<Float> vector = embed(base, content);

        // 1) 业务行先落库（content/metadata/vector_id，向量列已解耦；metadata 走 XML ::jsonb 转换）
        var row = new KnowledgeChunk();
        row.setKnowledgeBaseId(knowledgeBaseId);
        row.setDocumentId(document.getId());
        row.setChunkIndex(nextIndex);
        row.setContent(content);
        row.setVectorId(vectorId);
        row.setMetadata(MANUAL_METADATA);
        row.setContentHash(sha256Hex(content));
        row.setSourceType("TEXT");
        row.setKeywordTokens(keywordTokens(content));
        chunkMapper.batchSaveChunks(List.of(row));

        // 2) 向量写入知识库绑定的存储实例集合（主键 = vector_id）
        VectorStore store = vectorStoreResolver.vectorStore(base.getVectorStoreInstanceId());
        store.store(List.of(new VectorStore.VectorChunk(
                vectorId, knowledgeBaseId, document.getId(), nextIndex, content, vector,
                MANUAL_METADATA, null, sha256Hex(content), "TEXT")));

        KnowledgeChunk created = chunkMapper.selectOne(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getVectorId, vectorId));
        if (created == null) {
            throw new ServiceException("切片已写入但回查失败，请刷新列表确认");
        }
        shiftChunkCount(document, 1);
        return toVo(created, document.getTitle());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeChunkVo updateChunk(Long knowledgeBaseId, Long chunkId, KbChunkEditBo bo) {
        String content = requireContent(bo == null ? null : bo.content());
        KnowledgeChunk chunk = requireChunkOfBase(knowledgeBaseId, chunkId);
        KnowledgeDocument document = documentMapper.selectById(chunk.getDocumentId());
        requireDocumentIndexSettled(document);
        KnowledgeBase base = requireKnowledgeBase(knowledgeBaseId);

        // 业务行更新内容（向量列已解耦，无需再写 embedding 字面量）；关键词索引随内容同步刷新
        var update = new KnowledgeChunk();
        update.setId(chunk.getId());
        update.setContent(content);
        update.setContentHash(sha256Hex(content));
        update.setKeywordTokens(keywordTokens(content));
        chunkMapper.updateById(update);

        // 集合内向量重嵌：删旧写新，保持 vector_id 主键不变
        if (chunk.getVectorId() != null) {
            VectorStore store = vectorStoreResolver.vectorStore(base.getVectorStoreInstanceId());
            List<Float> vector = embed(base, content);
            store.deleteByVectorId(knowledgeBaseId, chunk.getVectorId());
            store.store(List.of(new VectorStore.VectorChunk(
                    chunk.getVectorId(), knowledgeBaseId, chunk.getDocumentId(), chunk.getChunkIndex(),
                    content, vector, MANUAL_METADATA, null, sha256Hex(content), "TEXT")));
        }

        KnowledgeChunk refreshed = chunkMapper.selectById(chunkId);
        return toVo(refreshed, document == null ? null : document.getTitle());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteChunk(Long knowledgeBaseId, Long chunkId) {
        KnowledgeChunk chunk = requireChunkOfBase(knowledgeBaseId, chunkId);
        KnowledgeDocument document = documentMapper.selectById(chunk.getDocumentId());
        requireDocumentIndexSettled(document);
        KnowledgeBase base = requireKnowledgeBase(knowledgeBaseId);
        // 先删集合内向量，再删业务行
        if (chunk.getVectorId() != null) {
            VectorStore store = vectorStoreResolver.vectorStore(base.getVectorStoreInstanceId());
            store.deleteByVectorId(knowledgeBaseId, chunk.getVectorId());
        }
        chunkMapper.deleteById(chunkId);
        shiftChunkCount(document, -1);
    }

    // ---------- helpers ----------

    private KnowledgeBase requireKnowledgeBase(Long knowledgeBaseId) {
        KnowledgeBase base = knowledgeBaseId == null ? null : knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (base == null) {
            throw new ServiceException("知识库不存在: " + knowledgeBaseId);
        }
        return base;
    }

    private KnowledgeDocument requireDocumentOfBase(Long knowledgeBaseId, Long documentId) {
        KnowledgeDocument document = documentId == null ? null : documentMapper.selectById(documentId);
        if (document == null || !knowledgeBaseId.equals(document.getKnowledgeBaseId())) {
            throw new ServiceException("文档不存在或不属于该知识库: " + documentId);
        }
        return document;
    }

    private KnowledgeChunk requireChunkOfBase(Long knowledgeBaseId, Long chunkId) {
        KnowledgeChunk chunk = chunkId == null ? null : chunkMapper.selectById(chunkId);
        if (chunk == null || !knowledgeBaseId.equals(chunk.getKnowledgeBaseId())) {
            throw new ServiceException("切片不存在或不属于该知识库: " + chunkId);
        }
        return chunk;
    }

    /**
     * 所属文档未进入索引终态时拒绝手工改动：Worker 重建会先删除该文档的全部切片与向量，
     * 窗口内的手工增删改会被静默覆盖（分片计数也会永久漂移）。
     */
    private static void requireDocumentIndexSettled(KnowledgeDocument document) {
        if (document != null && (document.getStatus() == null || document.getStatus() < DOC_STATUS_COMPLETED)) {
            throw new ServiceException("所属文档尚未完成索引（解析中/处理中），暂不可手工增删或修改切片");
        }
    }

    /**
     * 增量维护所属文档的分片数量（Worker 重新索引时会整体重算，此处只做增量）。
     */
    private void shiftChunkCount(KnowledgeDocument document, int delta) {
        if (document == null) {
            return;
        }
        int current = document.getChunkCount() == null ? 0 : document.getChunkCount();
        var patch = new KnowledgeDocument();
        patch.setId(document.getId());
        patch.setChunkCount(Math.max(0, current + delta));
        documentMapper.updateById(patch);
    }

    private static String requireContent(String content) {
        if (StrUtil.isBlank(content)) {
            throw new ServiceException("切片内容不能为空");
        }
        if (content.length() > MAX_CHUNK_CHARS) {
            throw new ServiceException("切片内容超过上限（" + MAX_CHUNK_CHARS + " 字符）");
        }
        return content;
    }

    /** 关键词索引（jieba INDEX 分词，与 Worker 重建同一口径） */
    private String[] keywordTokens(String content) {
        return textTokenizer.tokenizeForIndex(content).toArray(String[]::new);
    }

    private List<Float> embed(KnowledgeBase base, String content) {
        try {
            return embeddingClient.embed(content, base.getEmbeddingModelId(),
                    base.getDimensionOfVectorModel());
        } catch (Exception exception) {
            throw new ServiceException("生成向量失败: " + exception.getMessage());
        }
    }

    private Map<Long, String> documentTitles(List<Long> documentIds) {
        List<Long> ids = documentIds.stream().distinct().filter(java.util.Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return documentMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(KnowledgeDocument::getId, KnowledgeDocument::getTitle,
                        (a, b) -> a, java.util.LinkedHashMap::new));
    }

    private static KnowledgeChunkVo toVo(KnowledgeChunk chunk, String documentTitle) {
        KnowledgeChunkVo vo = new KnowledgeChunkVo();
        vo.setId(chunk.getId());
        vo.setKnowledgeBaseId(chunk.getKnowledgeBaseId());
        vo.setDocumentId(chunk.getDocumentId());
        vo.setDocumentTitle(documentTitle);
        vo.setChunkIndex(chunk.getChunkIndex());
        vo.setContent(chunk.getContent());
        vo.setParagraphIndex(chunk.getParagraphIndex());
        vo.setTokenCount(chunk.getTokenCount());
        vo.setVectorId(chunk.getVectorId());
        vo.setContentHash(chunk.getContentHash());
        vo.setSourceType(chunk.getSourceType());
        vo.setCreatedAt(chunk.getCreatedAt());
        vo.setUpdatedAt(chunk.getUpdatedAt());
        return vo;
    }

    private static String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }
}
