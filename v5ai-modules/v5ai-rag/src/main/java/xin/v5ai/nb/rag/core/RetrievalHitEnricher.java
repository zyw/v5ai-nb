package xin.v5ai.nb.rag.core;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.rag.core.store.VectorStore.RetrievalHit;
import xin.v5ai.nb.rag.service.IKnowledgeChunkService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 检索命中回填：向量存储（pgvector 独立向量表 / Milvus）检索只返回 {@code (vectorId, score)}，
 * 内容/元数据以业务表 {@code v5ai_knowledge_chunk} 为准，本组件按 vectorId 批量回查补齐
 * content/metadata（并以业务行修正 documentId/chunkIndex）。关键词命中若已内联内容则原样保留。
 */
@Component
@RequiredArgsConstructor
public class RetrievalHitEnricher {

    private final IKnowledgeChunkService chunkService;

    public List<RetrievalHit> enrich(List<RetrievalHit> hits) {
        if (CollUtil.isEmpty(hits)) {
            return hits;
        }
        List<String> missingVectorIds = hits.stream()
                .filter(hit -> hit.vectorId() != null && hit.content() == null)
                .map(RetrievalHit::vectorId)
                .distinct()
                .toList();
        if (missingVectorIds.isEmpty()) {
            return hits;
        }
        Map<String, RetrievalHit> byVectorId = new HashMap<>();
        for (var row : chunkService.selectByVectorIds(missingVectorIds)) {
            byVectorId.put(row.getVectorId(), new RetrievalHit(
                    row.getVectorId(),
                    row.getKnowledgeBaseId(),
                    row.getDocumentId(),
                    row.getChunkIndex(),
                    row.getContent(),
                    StrUtil.toString(row.getMetadata()),
                    0));
        }
        var enriched = new ArrayList<RetrievalHit>(hits.size());
        for (RetrievalHit hit : hits) {
            if (hit.content() == null && hit.vectorId() != null) {
                RetrievalHit row = byVectorId.get(hit.vectorId());
                if (row != null) {
                    enriched.add(new RetrievalHit(
                            hit.vectorId(), row.knowledgeBaseId(), row.documentId(), row.chunkIndex(),
                            row.content(), row.metadata(), hit.score()));
                    continue;
                }
            }
            enriched.add(hit);
        }
        return enriched;
    }
}
