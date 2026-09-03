package xin.v5ai.nb.rag.core.store;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import xin.v5ai.nb.common.milvus.domain.MilvusVectorConfigDO;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Milvus 向量存储：collection 按知识库隔离（{@code {collectionPrefix}_{knowledgeBaseId}}），
 * 动态字段承载 content / 元数据与 id 信息，首次写入时惰性建 collection（autoID + 动态字段）。
 */
public class MilvusVectorStore implements VectorStore {

    private final MilvusClientV2 client;
    private final MilvusVectorConfigDO config;

    public MilvusVectorStore(MilvusClientV2 client, MilvusVectorConfigDO config) {
        this.client = client;
        this.config = config;
    }

    @Override
    public void store(List<VectorChunk> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        Long knowledgeBaseId = chunks.get(0).knowledgeBaseId();
        int dimension = chunks.get(0).embedding() == null ? 0 : chunks.get(0).embedding().size();
        ensureCollection(knowledgeBaseId, dimension);

        List<JsonObject> rows = new ArrayList<>(chunks.size());
        for (VectorChunk chunk : chunks) {
            rows.add(rowJson(chunk));
        }
        client.insert(InsertReq.builder().collectionName(collectionName(knowledgeBaseId)).data(rows).build());
    }

    @Override
    public void deleteByDocumentId(Long knowledgeBaseId, Long documentId) {
        client.delete(DeleteReq.builder()
                .collectionName(collectionName(knowledgeBaseId))
                .filter("documentId == " + documentId)
                .build());
    }

    @Override
    public List<RetrievalHit> search(List<Long> knowledgeBaseIds, List<Float> queryVector, int topK) {
        var hits = new ArrayList<RetrievalHit>();
        for (Long knowledgeBaseId : knowledgeBaseIds) {
            try {
                SearchResp resp = client.search(SearchReq.builder()
                        .collectionName(collectionName(knowledgeBaseId))
                        .data(List.of(new FloatVec(queryVector)))
                        .annsField("vector")
                        .topK(topK)
                        .outputFields(List.of("content", "knowledgeBaseId", "documentId", "chunkIndex", "metadata"))
                        .build());
                if (resp.getSearchResults() == null) {
                    continue;
                }
                for (List<SearchResp.SearchResult> perQuery : resp.getSearchResults()) {
                    for (SearchResp.SearchResult result : perQuery) {
                        hits.add(toHit(result));
                    }
                }
            } catch (Exception ignored) {
                // 集合不存在或后端不可用：该知识库降级为空，继续其余知识库
            }
        }
        hits.sort(Comparator.comparingDouble(RetrievalHit::score).reversed());
        return hits.size() > topK ? hits.subList(0, topK) : hits;
    }

    private void ensureCollection(Long knowledgeBaseId, int dimension) {
        String name = collectionName(knowledgeBaseId);
        Boolean exists = client.hasCollection(HasCollectionReq.builder().collectionName(name).build());
        if (Boolean.FALSE.equals(exists)) {
            client.createCollection(CreateCollectionReq.builder()
                    .collectionName(name)
                    .dimension(dimension)
                    .metricType(config.getMetricType() == null ? "COSINE" : config.getMetricType())
                    .autoID(true)
                    .enableDynamicField(true)
                    .build());
        }
    }

    private JsonObject rowJson(VectorChunk chunk) {
        JsonArray vector = new JsonArray();
        for (Float f : chunk.embedding()) {
            vector.add(f);
        }
        JsonObject row = new JsonObject();
        row.add("vector", vector);
        row.addProperty("content", chunk.content());
        row.addProperty("knowledgeBaseId", chunk.knowledgeBaseId());
        row.addProperty("documentId", chunk.documentId());
        row.addProperty("chunkIndex", chunk.chunkIndex());
        row.addProperty("metadata", chunk.metadata() == null ? "" : chunk.metadata());
        return row;
    }

    private RetrievalHit toHit(SearchResp.SearchResult result) {
        Map<String, Object> entity = result.getEntity();
        double score = result.getScore() == null ? 0.0 : result.getScore();
        return new RetrievalHit(
                number(entity.get("knowledgeBaseId")).longValue(),
                number(entity.get("documentId")).longValue(),
                number(entity.get("chunkIndex")).intValue(),
                String.valueOf(entity.get("content")),
                String.valueOf(entity.get("metadata")),
                score);
    }

    private static Number number(Object value) {
        return value instanceof Number number ? number : 0;
    }

    public void close() {
        client.close();
    }

    private String collectionName(Long knowledgeBaseId) {
        return config.getCollectionPrefix() + "_" + knowledgeBaseId;
    }
}
