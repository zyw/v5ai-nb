package xin.v5ai.nb.rag.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.common.core.exception.ServiceException;
import xin.v5ai.nb.rag.core.config.RagConfigDO;
import xin.v5ai.nb.rag.core.dto.RetrievalCitation;
import xin.v5ai.nb.rag.core.dto.RetrievalResult;
import xin.v5ai.nb.rag.core.fusion.FusionStrategy;
import xin.v5ai.nb.rag.core.fusion.RetrievalFusion;
import xin.v5ai.nb.rag.core.store.KeywordStore;
import xin.v5ai.nb.rag.core.store.VectorStore;
import xin.v5ai.nb.rag.core.store.VectorStoreResolver;
import xin.v5ai.nb.rag.domain.vo.KnowledgeBaseVo;
import xin.v5ai.nb.rag.service.IAgentKnowledgeBindingService;
import xin.v5ai.nb.rag.service.IKnowledgeBaseService;
import xin.v5ai.nb.rag.service.IKnowledgeDocumentService;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds the retrieval context for a runtime request: resolves the knowledge bases bound to the
 * application, groups them by vector store instance (and search engine instance for keywords),
 * embeds the query, performs vector search with a keyword fallback, and formats hits into context.
 * <p>
 * 融合语义（与调试检索口径对齐，见 {@link RetrievalFusion}）：
 * <ul>
 *   <li>默认「快速路径」：向量优先（每库组 top {@link #INJECT_TOP_K}），向量为空才回退关键词，简单去重合并；</li>
 *   <li>仅当该 AgentDTO 绑定的知识库<b>全部</b>显式配置且一致（{@code config.searchParams.fusionStrategy} =
 *       {@code RRF} 或 {@code WEIGHTED_SUM}）时执行双路融合：候选数 = {@code max(注入条数×3, 30)}，
 *       融合后截断到注入条数——使「调试里显式选融合并保存」的库在线上按同口径检索；</li>
 *   <li>任一库未显式配置 / 配置不一致 / 策略非法时维持快速路径并告警；注入条数保持独立预算
 *       {@link #INJECT_TOP_K}（不读 {@code resultCount}，避免上下文膨胀）。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetrievalContextBuilder {
    /** 注入上下文的命中条数预算（独立于调试侧 resultCount，避免上下文膨胀）。 */
    private static final int INJECT_TOP_K = 4;
    /** 融合候选数下限（与调试链路一致）。 */
    private static final int MIN_CANDIDATES = 30;
    private static final int DEFAULT_RRF_K = 60;
    private static final double DEFAULT_DENSE_WEIGHT = 0.5;

    private final IAgentKnowledgeBindingService bindingService;
    private final IKnowledgeBaseService baseService;
    private final VectorStoreResolver vectorStoreResolver;
    private final EmbeddingClient embeddingClient;
    private final IKnowledgeDocumentService documentService;
    private final RetrievalHitEnricher hitEnricher;

    public RetrievalResult build(String agentKey, String query) {
        var knowledgeBaseIds = bindingService.findByAgentKey(agentKey);
        if (knowledgeBaseIds.isEmpty()) {
            return RetrievalResult.empty();
        }
        Map<Long, KnowledgeBaseVo> bases = new LinkedHashMap<>();
        for (Long knowledgeBaseId : knowledgeBaseIds) {
            KnowledgeBaseVo base = baseService.getKnowledgeBase(knowledgeBaseId);
            if (base != null) {
                bases.put(knowledgeBaseId, base);
            }
        }
        if (bases.isEmpty()) {
            return RetrievalResult.empty();
        }
        return retrieve(bases, query);
    }

    /**
     * 单库检索（智能调用 {@code rag_search} 工具用）：校验知识库确已绑定到 AgentDTO，
     * 再对该单个知识库执行与 {@link #build} 相同的检索/融合/回填/格式化管线。
     */
    public RetrievalResult search(String agentKey, long knowledgeBaseId, String query) {
        var knowledgeBaseIds = bindingService.findByAgentKey(agentKey);
        if (!knowledgeBaseIds.contains(knowledgeBaseId)) {
            throw new ServiceException("knowledge base " + knowledgeBaseId + " is not bound to agent '" + agentKey + "'");
        }
        KnowledgeBaseVo base = baseService.getKnowledgeBase(knowledgeBaseId);
        if (base == null) {
            return RetrievalResult.empty();
        }
        return retrieve(new LinkedHashMap<>(Map.of(knowledgeBaseId, base)), query);
    }

    private RetrievalResult retrieve(Map<Long, KnowledgeBaseVo> bases, String query) {
        List<VectorStore.RetrievalHit> hits;
        FusionSettings fusion = resolveFusionSettings(bases);
        if (fusion != null) {
            hits = fusedRetrieval(bases, query, fusion);
        } else {
            try {
                hits = vectorSearch(bases, query, INJECT_TOP_K);
            } catch (Exception exception) {
                log.warn("runtime vector search failed, fallback to keyword: {}", exception.getMessage());
                hits = List.of();
            }
            if (hits.isEmpty()) {
                hits = keywordSearch(bases, query, INJECT_TOP_K);
            }
        }
        if (hits.isEmpty()) {
            return RetrievalResult.empty();
        }
        // 向量存储检索只返回 (vectorId, score)：按业务表回填内容/元数据
        hits = hitEnricher.enrich(hits);
        return formatContext(hits);
    }

    /**
     * 显式融合路径：双路候选检索（候选数 = max(注入条数×3, 30)）→ {@link RetrievalFusion} 融合 → 截断到注入条数。
     * 单路检索异常不中断（另一路有结果仍可融合/兜底）。
     */
    private List<VectorStore.RetrievalHit> fusedRetrieval(Map<Long, KnowledgeBaseVo> bases, String query,
                                                          FusionSettings fusion) {
        int candidates = Math.max(INJECT_TOP_K * 3, MIN_CANDIDATES);
        List<VectorStore.RetrievalHit> vector;
        try {
            vector = vectorSearch(bases, query, candidates);
        } catch (Exception exception) {
            log.warn("runtime fused vector search failed: {}", exception.getMessage());
            vector = List.of();
        }
        List<VectorStore.RetrievalHit> keyword;
        try {
            keyword = keywordSearch(bases, query, candidates);
        } catch (Exception exception) {
            log.warn("runtime fused keyword search failed: {}", exception.getMessage());
            keyword = List.of();
        }
        List<VectorStore.RetrievalHit> fused = RetrievalFusion.fuse(
                vector, keyword, fusion.strategy(), fusion.rrfK(), fusion.denseWeight());
        return fused.size() > INJECT_TOP_K ? fused.subList(0, INJECT_TOP_K) : fused;
    }

    /**
     * 融合适用性判定（与调试口径对齐的运行时侧规则）：
     * 仅当<b>全部</b>绑定知识库显式配置融合（RRF / WEIGHTED_SUM）且参数一致时返回该配置，否则返回 {@code null}
     * 走默认快速路径。未显式配置的库保持「向量优先 + 关键词兜底」原行为（不引入无条件双路检索的开销）。
     */
    private FusionSettings resolveFusionSettings(Map<Long, KnowledgeBaseVo> bases) {
        FusionSettings first = null;
        for (KnowledgeBaseVo base : bases.values()) {
            FusionSettings settings = settingsOf(base);
            if (settings == null) {
                return null;
            }
            if (first == null) {
                first = settings;
            } else if (!first.equals(settings)) {
                log.warn("AgentDTO 绑定知识库的融合检索配置不一致（{} vs {}），回退默认检索路径", first, settings);
                return null;
            }
        }
        return first;
    }

    private FusionSettings settingsOf(KnowledgeBaseVo base) {
        RagConfigDO.SearchParams sp = base.getConfig() == null ? null : base.getConfig().getSearchParams();
        if (sp == null || sp.getFusionStrategy() == null || sp.getFusionStrategy().isBlank()) {
            return null;
        }
        FusionStrategy strategy;
        try {
            strategy = FusionStrategy.parse(sp.getFusionStrategy());
        } catch (ServiceException exception) {
            log.warn("kb {} 配置了不支持的融合策略（{}），回退默认检索路径",
                    base.getId(), sp.getFusionStrategy());
            return null;
        }
        if (strategy != FusionStrategy.RRF && strategy != FusionStrategy.WEIGHTED_SUM) {
            return null; // 仅显式 RRF / WEIGHTED_SUM 在运行时执行双路融合（VECTOR/KEYWORD 即默认路径行为）
        }
        int rrfK = sp.getRrfK() == null ? DEFAULT_RRF_K : RetrievalFusion.clampRrfK(sp.getRrfK());
        double denseWeight = RetrievalFusion.clampWeight(
                sp.getDenseWeight() == null ? DEFAULT_DENSE_WEIGHT : sp.getDenseWeight());
        return new FusionSettings(strategy, rrfK, denseWeight);
    }

    /** 融合参数快照（record equals 用于「全库配置一致」判定）。 */
    private record FusionSettings(FusionStrategy strategy, int rrfK, double denseWeight) {
    }

    /**
     * 按「向量存储实例 + 嵌入模型 + 冻结维度」分组：组内共用一次查询向量（维度一致），
     * 每组以该组知识库的嵌入模型与维度编码查询并检索。
     */
    private List<VectorStore.RetrievalHit> vectorSearch(Map<Long, KnowledgeBaseVo> bases, String query, int topK) {
        Map<String, List<KnowledgeBaseVo>> groups = new LinkedHashMap<>();
        for (KnowledgeBaseVo base : bases.values()) {
            String key = groupKey(base.getVectorStoreInstanceId(), base.getEmbeddingModelId(),
                    base.getDimensionOfVectorModel());
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(base);
        }
        var hits = new ArrayList<VectorStore.RetrievalHit>();
        for (List<KnowledgeBaseVo> group : groups.values()) {
            KnowledgeBaseVo first = group.get(0);
            VectorStore store = vectorStoreResolver.vectorStore(first.getVectorStoreInstanceId());
            var queryVector = embeddingClient.embed(query, first.getEmbeddingModelId(),
                    first.getDimensionOfVectorModel());
            List<Long> baseIds = group.stream().map(KnowledgeBaseVo::getId).toList();
            hits.addAll(store.search(baseIds, queryVector, topK));
        }
        return merge(hits, topK);
    }

    private static String groupKey(Long storeId, Long modelId, Integer dimension) {
        return storeId + ":" + modelId + ":" + (dimension == null ? 0 : dimension);
    }

    private List<VectorStore.RetrievalHit> keywordSearch(Map<Long, KnowledgeBaseVo> bases, String keyword, int topK) {
        Map<Long, List<Long>> searchEngineGroups = new LinkedHashMap<>();
        Set<Long> withSearchEngine = new HashSet<>();
        for (Map.Entry<Long, KnowledgeBaseVo> entry : bases.entrySet()) {
            KnowledgeBaseVo base = entry.getValue();
            if (Boolean.TRUE.equals(base.getSearchEngineEnable()) && base.getSearchEngineInstanceId() != null) {
                searchEngineGroups.computeIfAbsent(base.getSearchEngineInstanceId(), k -> new ArrayList<>())
                        .add(entry.getKey());
                withSearchEngine.add(entry.getKey());
            }
        }
        var hits = new ArrayList<VectorStore.RetrievalHit>();
        for (Map.Entry<Long, List<Long>> group : searchEngineGroups.entrySet()) {
            KeywordStore store = vectorStoreResolver.keywordStore(group.getKey());
            if (store != null) {
                hits.addAll(store.keywordSearch(group.getValue(), keyword, topK));
            }
        }
        // 未配置搜索引擎的知识库，退回向量库原生的关键词检索（PG ILIKE / ES match）；Milvus 无则跳过
        Map<Long, List<Long>> nativeGroups = new LinkedHashMap<>();
        for (Map.Entry<Long, KnowledgeBaseVo> entry : bases.entrySet()) {
            if (!withSearchEngine.contains(entry.getKey())) {
                nativeGroups.computeIfAbsent(entry.getValue().getVectorStoreInstanceId(), k -> new ArrayList<>())
                        .add(entry.getKey());
            }
        }
        for (Map.Entry<Long, List<Long>> group : nativeGroups.entrySet()) {
            VectorStore store = vectorStoreResolver.vectorStore(group.getKey());
            if (store instanceof KeywordStore keywordStore) {
                hits.addAll(keywordStore.keywordSearch(group.getValue(), keyword, topK));
            }
        }
        return merge(hits, topK);
    }

    private List<VectorStore.RetrievalHit> merge(List<VectorStore.RetrievalHit> hits, int topK) {
        if (hits.isEmpty()) {
            return List.of();
        }
        var seen = new HashSet<String>();
        var merged = new ArrayList<VectorStore.RetrievalHit>();
        hits.stream()
                .sorted(Comparator.comparingDouble(VectorStore.RetrievalHit::score).reversed())
                .forEach(hit -> {
                    String key = hit.knowledgeBaseId() + ":" + hit.documentId() + ":" + hit.chunkIndex();
                    if (seen.add(key)) {
                        merged.add(hit);
                    }
                });
        return merged.size() > topK ? merged.subList(0, topK) : merged;
    }

    /**
     * 组装注入用的上下文文本，同时把查到的文档标题留下来组成引用列表——
     * 标题只查一次，引用展示（RETRIEVAL 事件）不需要再查一遍业务表。
     */
    private RetrievalResult formatContext(List<VectorStore.RetrievalHit> hits) {
        var lines = new ArrayList<String>();
        var citations = new ArrayList<RetrievalCitation>();
        lines.add("[Knowledge Base Context]");
        int index = 1;
        for (var hit : hits) {
            var document = documentService.findById(hit.documentId());
            var title = document == null ? "doc-" + hit.documentId() : document.getTitle();
            lines.add("%d. (%s, chunk %d): %s".formatted(index++, title, hit.chunkIndex(), hit.content()));
            citations.add(new RetrievalCitation(hit.knowledgeBaseId(), hit.documentId(), title,
                    hit.chunkIndex(), hit.content(), hit.score()));
        }
        return new RetrievalResult(lines.stream().collect(Collectors.joining("\n")), citations);
    }
}
