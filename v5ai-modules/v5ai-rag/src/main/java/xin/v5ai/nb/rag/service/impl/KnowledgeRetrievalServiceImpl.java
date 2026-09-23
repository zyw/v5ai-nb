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
import xin.v5ai.nb.rag.core.RetrievalHitEnricher;
import xin.v5ai.nb.rag.core.config.RagConfigDO;
import xin.v5ai.nb.rag.core.fusion.FusionStrategy;
import xin.v5ai.nb.rag.core.fusion.RetrievalFusion;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库检索调试实现。
 * <p>
 * 语义口径：
 * <ul>
 *   <li>向量路得分 = 余弦相似度（0~1）；关键词路得分按存储实现（pgvector ILIKE 粗检恒 1.0 / ES 用 _score）。</li>
 *   <li>阈值过滤仅作用于向量相似度（关键词命中的粗检语义无阈值意义）。</li>
 *   <li>融合策略经 {@link FusionStrategy#parse} 校验：未知取值直接抛 {@link ServiceException}（不静默降级）；
 *       融合实现复用 {@link RetrievalFusion}（与运行时注入同一套逻辑）。</li>
 *   <li>RRF 策略下 rrfK 须在 1~200（{@link RetrievalFusion#requireRrfK}），越界拒绝；非 RRF 策略不校验。</li>
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
    private static final double DEFAULT_DENSE_WEIGHT = 0.5;
    private static final Duration REWRITE_TIMEOUT = Duration.ofSeconds(20);

    private final IKnowledgeBaseService knowledgeBaseService;
    private final EmbeddingClient embeddingClient;
    private final VectorStoreResolver vectorStoreResolver;
    private final KnowledgeDocumentMapper documentMapper;
    private final ModelChatClient modelChatClient;
    private final RetrievalHitEnricher hitEnricher;

    @Override
    public List<KbHitVo> retrieve(Long knowledgeBaseId, KbRetrieveBo request) {
        KnowledgeBaseVo base = requireBase(knowledgeBaseId);
        RagConfigDO.SearchParams stored = base.getConfig() == null || base.getConfig().getSearchParams() == null
                ? null : base.getConfig().getSearchParams();
        Long rewriteModelId = resolveRewriteModelId(request, base);
        String query = requireQuery(request == null ? null : request.query());
        int resultCount = RetrievalFusion.clamp(optional(request == null ? null : request.resultCount(),
                stored == null ? null : stored.getResultCount(), DEFAULT_RESULT_COUNT), 1, 100);
        boolean rewriteEnabled = optional(request == null ? null : request.questionRewrite(),
                stored == null ? null : stored.getQuestionRewrite(), false);
        boolean thresholdEnabled = optional(request == null ? null : request.thresholdEnabled(),
                stored == null ? null : stored.getThresholdEnabled(), false);
        Double threshold = request == null ? null : request.threshold();
        if (threshold == null && stored != null) {
            threshold = stored.getThreshold();
        }
        FusionStrategy strategy = FusionStrategy.parse(optional(request == null ? null : request.fusionStrategy(),
                stored == null ? null : stored.getFusionStrategy(), FusionStrategy.DEFAULT.name()));
        int rrfK = resolveRrfK(request, stored, strategy);
        double denseWeight = RetrievalFusion.clampWeight(optional(request == null ? null : request.denseWeight(),
                stored == null ? null : stored.getDenseWeight(), DEFAULT_DENSE_WEIGHT));

        String effectiveQuery = rewriteEnabled ? rewrite(query, rewriteModelId) : query;
        if (!effectiveQuery.equals(query)) {
            log.info("kb {} query rewritten: {} -> {}", knowledgeBaseId, query, effectiveQuery);
        }

        VectorStore store = vectorStoreResolver.vectorStore(base.getVectorStoreInstanceId());
        int candidates = Math.max(resultCount * 3, 30);

        List<VectorStore.RetrievalHit> vectorHits = vectorHits(base, store, effectiveQuery, candidates);
        List<VectorStore.RetrievalHit> keywordHits = keywordHits(base, store, effectiveQuery, candidates);
        if (thresholdEnabled && threshold != null) {
            vectorHits = RetrievalFusion.filterByThreshold(vectorHits, threshold);
        }

        List<VectorStore.RetrievalHit> merged = RetrievalFusion.fuse(
                vectorHits, keywordHits, strategy, rrfK, denseWeight);
        if (merged.size() > resultCount) {
            merged = merged.subList(0, resultCount);
        }
        return toHits(knowledgeBaseId, hitEnricher.enrich(merged));
    }

    // ---------- retrieval ----------

    private List<VectorStore.RetrievalHit> vectorHits(KnowledgeBaseVo base, VectorStore store,
                                                      String query, int candidates) {
        try {
            List<Float> vector = embeddingClient.embed(query, base.getEmbeddingModelId(),
                    base.getDimensionOfVectorModel());
            return store.search(List.of(base.getId()), vector, candidates);
        } catch (Exception exception) {
            log.warn("kb {} vector search failed: {}", base.getId(), exception.getMessage());
            return List.of();
        }
    }

    /**
     * 关键词这一路的后端选择，与运行时 {@code RetrievalContextBuilder.keywordSearch} 同规则：
     * 知识库显式配了搜索引擎实例时优先用它；否则退回向量实例自带的关键词能力
     * （PG_VECTOR 走业务库 BM25、ES 走 match）。Milvus 只承载向量、没有关键词能力，
     * 不配搜索引擎时这一路为空——融合退化为纯向量，不是错误。
     */
    private List<VectorStore.RetrievalHit> keywordHits(KnowledgeBaseVo base, VectorStore store,
                                                       String keyword, int candidates) {
        KeywordStore keywordStore = null;
        if (Boolean.TRUE.equals(base.getSearchEngineEnable()) && base.getSearchEngineInstanceId() != null) {
            keywordStore = vectorStoreResolver.keywordStore(base.getSearchEngineInstanceId());
        }
        if (keywordStore == null && store instanceof KeywordStore nativeStore) {
            keywordStore = nativeStore;
        }
        if (keywordStore == null) {
            return List.of();
        }
        try {
            return keywordStore.keywordSearch(List.of(base.getId()), keyword, candidates);
        } catch (Exception exception) {
            log.warn("kb {} keyword search failed: {}", base.getId(), exception.getMessage());
            return List.of();
        }
    }

    // ---------- params ----------

    /**
     * rrfK 取值链：请求 → 知识库配置 → 默认 60。
     * 仅 RRF 策略下参与并校验（越界抛 {@link ServiceException}）；其余策略该值不生效、不校验。
     */
    private static int resolveRrfK(KbRetrieveBo request, RagConfigDO.SearchParams stored, FusionStrategy strategy) {
        Integer value = request == null ? null : request.rrfK();
        if (value == null && stored != null) {
            value = stored.getRrfK();
        }
        int rrfK = value == null ? DEFAULT_RRF_K : value;
        return strategy == FusionStrategy.RRF ? RetrievalFusion.requireRrfK(rrfK) : rrfK;
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

    private static <T> T optional(T value, T fallback, T defaultValue) {
        return value != null ? value : (fallback != null ? fallback : defaultValue);
    }

    private static double round(double score) {
        return Math.round(score * 10000.0) / 10000.0;
    }
}
