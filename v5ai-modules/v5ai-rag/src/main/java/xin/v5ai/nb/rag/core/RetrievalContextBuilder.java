package xin.v5ai.nb.rag.core;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.rag.core.dto.RetrievalResult;
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
 */
@Component
@RequiredArgsConstructor
public class RetrievalContextBuilder {
    private static final int DEFAULT_TOP_K = 4;

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

        List<VectorStore.RetrievalHit> hits;
        try {
            hits = vectorSearch(bases, query);
        } catch (Exception exception) {
            hits = List.of();
        }
        if (hits.isEmpty()) {
            hits = keywordSearch(bases, query);
        }
        if (hits.isEmpty()) {
            return RetrievalResult.empty();
        }
        // 向量存储检索只返回 (vectorId, score)：按业务表回填内容/元数据
        hits = hitEnricher.enrich(hits);
        return new RetrievalResult(formatContext(hits), hits);
    }

    /**
     * 按「向量存储实例 + 嵌入模型 + 冻结维度」分组：组内共用一次查询向量（维度一致），
     * 每组以该组知识库的嵌入模型与维度编码查询并检索。
     */
    private List<VectorStore.RetrievalHit> vectorSearch(Map<Long, KnowledgeBaseVo> bases, String query) {
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
            hits.addAll(store.search(baseIds, queryVector, DEFAULT_TOP_K));
        }
        return merge(hits);
    }

    private static String groupKey(Long storeId, Long modelId, Integer dimension) {
        return storeId + ":" + modelId + ":" + (dimension == null ? 0 : dimension);
    }

    private List<VectorStore.RetrievalHit> keywordSearch(Map<Long, KnowledgeBaseVo> bases, String keyword) {
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
                hits.addAll(store.keywordSearch(group.getValue(), keyword, DEFAULT_TOP_K));
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
                hits.addAll(keywordStore.keywordSearch(group.getValue(), keyword, DEFAULT_TOP_K));
            }
        }
        return merge(hits);
    }

    private List<VectorStore.RetrievalHit> merge(List<VectorStore.RetrievalHit> hits) {
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
        return merged.size() > DEFAULT_TOP_K ? merged.subList(0, DEFAULT_TOP_K) : merged;
    }

    private String formatContext(List<VectorStore.RetrievalHit> hits) {
        var lines = new ArrayList<String>();
        lines.add("[Knowledge Base Context]");
        int index = 1;
        for (var hit : hits) {
            var document = documentService.findById(hit.documentId());
            var title = document == null ? "doc-" + hit.documentId() : document.getTitle();
            lines.add("%d. (%s, chunk %d): %s".formatted(index++, title, hit.chunkIndex(), hit.content()));
        }
        return lines.stream().collect(Collectors.joining("\n"));
    }
}
