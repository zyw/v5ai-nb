package xin.v5ai.nb.rag.service.impl;

import cn.hutool.core.util.StrUtil;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.common.agentscope.core.ModelChatClient;
import xin.v5ai.nb.common.core.exception.ServiceException;
import xin.v5ai.nb.rag.core.EmbeddingClient;
import xin.v5ai.nb.rag.core.config.RagConfigDO;
import xin.v5ai.nb.rag.core.store.KeywordStore;
import xin.v5ai.nb.rag.core.store.VectorStore;
import xin.v5ai.nb.rag.core.store.VectorStoreResolver;
import xin.v5ai.nb.rag.domain.bo.KbRetrieveBo;
import xin.v5ai.nb.rag.domain.vo.KbHitVo;
import xin.v5ai.nb.rag.domain.vo.KnowledgeBaseVo;
import xin.v5ai.nb.rag.mapper.KnowledgeDocumentMapper;
import xin.v5ai.nb.rag.service.IKnowledgeBaseService;
import xin.v5ai.nb.rag.service.IKnowledgeRetrievalService;

import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库检索调试实现。
 * <p>
 * 语义口径：
 * <ul>
 *   <li>向量路得分 = 余弦相似度（0~1）；关键词路恒为 1.0（ILIKE 粗检，无相关度）。</li>
 *   <li>阈值过滤仅作用于向量相似度（关键词命中的粗检语义无阈值意义）。</li>
 *   <li>RRF 融合按两路名次计分 {@code sum(1/(rrfK + rank))}，与参考稿「RRF 倒数排名融合」一致；
 *       仅一路有结果时直接返回该路（保留其原始得分口径）。</li>
 *   <li>问题改写为「半真」能力：需要可用对话模型（请求 modelId 或知识库 ModelParams.modelId）；
 *       模型缺失或调用失败时自动跳过改写并告警，不影响检索主流程。</li>
 * </ul>
 *
 * @author ZYW
 * @since 2026-09-06
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeRetrievalServiceImpl implements IKnowledgeRetrievalService {

    private static final int DEFAULT_RESULT_COUNT = 20;
    private static final int DEFAULT_RRF_K = 60;
    private static final String FUSION_RRF = "RRF";
    private static final String FUSION_VECTOR = "VECTOR";
    private static final String FUSION_KEYWORD = "KEYWORD";
    private static final Duration REWRITE_TIMEOUT = Duration.ofSeconds(20);

    private final IKnowledgeBaseService knowledgeBaseService;
    private final EmbeddingClient embeddingClient;
    private final VectorStoreResolver vectorStoreResolver;
    private final KnowledgeDocumentMapper documentMapper;
    private final ModelChatClient modelChatClient;

    @Override
    public List<KbHitVo> retrieve(Long knowledgeBaseId, KbRetrieveBo request) {
        KnowledgeBaseVo base = requireBase(knowledgeBaseId);
        RagConfigDO.SearchParams stored = base.getConfig() == null || base.getConfig().getSearchParams() == null
                ? null : base.getConfig().getSearchParams();
        Long rewriteModelId = resolveRewriteModelId(request, base);
        String query = requireQuery(request == null ? null : request.query());
        int resultCount = clamp(optional(request == null ? null : request.resultCount(),
                stored == null ? null : stored.getResultCount(), DEFAULT_RESULT_COUNT), 1, 100);
        boolean rewriteEnabled = optional(request == null ? null : request.questionRewrite(),
                stored == null ? null : stored.getQuestionRewrite(), false);
        boolean thresholdEnabled = optional(request == null ? null : request.thresholdEnabled(),
                stored == null ? null : stored.getThresholdEnabled(), false);
        Double threshold = request == null ? null : request.threshold();
        if (threshold == null && stored != null) {
            threshold = stored.getThreshold();
        }
        String fusionStrategy = normalize(optional(request == null ? null : request.fusionStrategy(),
                stored == null ? null : stored.getFusionStrategy(), FUSION_RRF));
        int rrfK = optional(request == null ? null : request.rrfK(),
                stored == null ? null : stored.getRrfK(), DEFAULT_RRF_K);

        String effectiveQuery = rewriteEnabled ? rewrite(query, rewriteModelId) : query;
        if (!effectiveQuery.equals(query)) {
            log.info("kb {} query rewritten: {} -> {}", knowledgeBaseId, query, effectiveQuery);
        }

        VectorStore store = vectorStoreResolver.vectorStore(base.getVectorStoreInstanceId());
        int candidates = Math.max(resultCount * 3, 30);

        List<VectorStore.RetrievalHit> vectorHits = vectorHits(base, store, effectiveQuery, candidates);
        List<VectorStore.RetrievalHit> keywordHits = keywordHits(base, store, effectiveQuery, candidates);
        if (thresholdEnabled && threshold != null) {
            vectorHits = filterByThreshold(vectorHits, threshold);
        }

        List<VectorStore.RetrievalHit> merged = fuse(vectorHits, keywordHits, fusionStrategy, rrfK);
        if (merged.size() > resultCount) {
            merged = merged.subList(0, resultCount);
        }
        return toHits(knowledgeBaseId, merged);
    }

    // ---------- retrieval ----------

    private List<VectorStore.RetrievalHit> vectorHits(KnowledgeBaseVo base, VectorStore store,
                                                      String query, int candidates) {
        try {
            List<Float> vector = embeddingClient.embed(query);
            return store.search(List.of(base.getId()), vector, candidates);
        } catch (Exception exception) {
            log.warn("kb {} vector search failed: {}", base.getId(), exception.getMessage());
            return List.of();
        }
    }

    private List<VectorStore.RetrievalHit> keywordHits(KnowledgeBaseVo base, VectorStore store,
                                                       String keyword, int candidates) {
        if (!(store instanceof KeywordStore keywordStore)) {
            return List.of();
        }
        try {
            return keywordStore.keywordSearch(List.of(base.getId()), keyword, candidates);
        } catch (Exception exception) {
            log.warn("kb {} keyword search failed: {}", base.getId(), exception.getMessage());
            return List.of();
        }
    }

    private static List<VectorStore.RetrievalHit> filterByThreshold(
            List<VectorStore.RetrievalHit> hits, double threshold) {
        return hits.stream()
                .filter(hit -> hit.score() >= threshold)
                .toList();
    }

    /**
     * 融合两路命中。语义见类注释。
     */
    private static List<VectorStore.RetrievalHit> fuse(List<VectorStore.RetrievalHit> vector,
                                                       List<VectorStore.RetrievalHit> keyword,
                                                       String strategy, int rrfK) {
        if (FUSION_VECTOR.equals(strategy)) {
            return vector;
        }
        if (FUSION_KEYWORD.equals(strategy)) {
            return keyword;
        }
        if (vector.isEmpty()) {
            return keyword;
        }
        if (keyword.isEmpty()) {
            return vector;
        }
        // RRF：以两路名次融合；去重键与运行时 merge 一致（库+文档+片序号）
        Map<String, Ranked> fused = new HashMap<>();
        for (int i = 0; i < vector.size(); i++) {
            VectorStore.RetrievalHit hit = vector.get(i);
            fused.computeIfAbsent(key(hit), k -> new Ranked(hit)).add(i + 1, rrfK);
        }
        for (int i = 0; i < keyword.size(); i++) {
            VectorStore.RetrievalHit hit = keyword.get(i);
            fused.computeIfAbsent(key(hit), k -> new Ranked(hit)).add(i + 1, rrfK);
        }
        return fused.values().stream()
                .sorted(Comparator.comparingDouble((Ranked r) -> r.score).reversed())
                .map(r -> new VectorStore.RetrievalHit(
                        r.hit.knowledgeBaseId(), r.hit.documentId(), r.hit.chunkIndex(),
                        r.hit.content(), r.hit.metadata(), r.score))
                .toList();
    }

    private static String key(VectorStore.RetrievalHit hit) {
        return hit.knowledgeBaseId() + ":" + hit.documentId() + ":" + hit.chunkIndex();
    }

    private static final class Ranked {
        private final String key;
        private final VectorStore.RetrievalHit hit;
        private double score;

        private Ranked(VectorStore.RetrievalHit hit) {
            this.key = key(hit);
            this.hit = hit;
        }

        private void add(int rank, int rrfK) {
            score += 1.0 / (rrfK + rank);
        }
    }

    // ---------- output ----------

    private List<KbHitVo> toHits(Long knowledgeBaseId, List<VectorStore.RetrievalHit> hits) {
        List<Long> documentIds = hits.stream()
                .map(VectorStore.RetrievalHit::documentId)
                .distinct()
                .toList();
        Map<Long, String> titles = documentIds.isEmpty() ? Map.of()
                : documentMapper.selectBatchIds(documentIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        doc -> doc.getId(), doc -> doc.getTitle(), (a, b) -> a, LinkedHashMap::new));
        return hits.stream()
                .map(hit -> new KbHitVo(
                        knowledgeBaseId,
                        hit.documentId(),
                        titles.get(hit.documentId()),
                        hit.chunkIndex(),
                        hit.content(),
                        round(hit.score())))
                .toList();
    }

    // ---------- rewrite ----------

    /**
     * 问题改写：经对话模型把用户问题改写为适合向量检索的表达。
     * 模型缺失或调用失败时回退原问题（半真能力，不影响检索主流程）。
     */
    private String rewrite(String query, Long modelId) {
        if (modelId == null) {
            log.warn("question rewrite enabled but no chat model available, skip rewrite");
            return query;
        }
        try {
            List<Msg> messages = List.of(
                    Msg.builder().role(MsgRole.SYSTEM)
                            .textContent("你是检索查询改写助手。把用户问题改写为更适合知识库向量检索的简洁查询，"
                                    + "保留关键词与语义。只输出改写后的查询，不要解释、不要加引号。")
                            .build(),
                    Msg.builder().role(MsgRole.USER).textContent(query).build());
            return modelChatClient.chatText(modelId, messages, REWRITE_TIMEOUT);
        } catch (Exception exception) {
            log.warn("question rewrite failed, fallback to original query: {}", exception.getMessage());
            return query;
        }
    }

    private Long resolveRewriteModelId(KbRetrieveBo request, KnowledgeBaseVo base) {
        Long modelId = request == null ? null : request.modelId();
        if (modelId != null) {
            return modelId;
        }
        RagConfigDO.ModelParams modelParams = base.getConfig() == null ? null : base.getConfig().getModelParams();
        return modelParams == null ? null : modelParams.getModelId();
    }

    // ---------- helpers ----------

    private KnowledgeBaseVo requireBase(Long knowledgeBaseId) {
        KnowledgeBaseVo base = knowledgeBaseId == null ? null : knowledgeBaseService.getKnowledgeBase(knowledgeBaseId);
        if (base == null) {
            throw new ServiceException("知识库不存在: " + knowledgeBaseId);
        }
        return base;
    }

    private static String requireQuery(String query) {
        if (StrUtil.isBlank(query)) {
            throw new ServiceException("请输入检索内容");
        }
        return query.trim();
    }

    private static String normalize(String strategy) {
        return strategy == null || strategy.isBlank() ? FUSION_RRF : strategy.trim().toUpperCase();
    }

    private static <T> T optional(T value, T fallback, T defaultValue) {
        return value != null ? value : (fallback != null ? fallback : defaultValue);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double round(double score) {
        return Math.round(score * 10000.0) / 10000.0;
    }
}
