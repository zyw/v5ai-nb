package xin.v5ai.nb.rag.core.store;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.index.request.ListIndexesReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.extern.slf4j.Slf4j;
import xin.v5ai.nb.common.milvus.domain.MilvusVectorConfigDO;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Milvus 向量存储：collection 按知识库隔离（{@code {collectionPrefix}_{knowledgeBaseId}}），
 * 集合主键 = 业务生成的 {@code vector_id}(VarChar)，首次写入惰性建 collection。
 *
 * <p>首次写入顺序为「建 collection → 建索引 → 插入 → 载入内存」，删除/检索对不存在的
 * collection 均幂等降级（删除跳过、检索按空处理），保证 Worker 幂等重建（先删旧向量再写）不会
 * 在首篇文档上因 collection 未建而失败。</p>
 */
@Slf4j
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
        // 载入内存后检索才可用（幂等，重复调用无副作用）
        loadCollection(knowledgeBaseId);
    }

    @Override
    public void deleteByDocumentId(Long knowledgeBaseId, Long documentId) {
        if (!collectionExists(knowledgeBaseId)) {
            return;
        }
        client.delete(DeleteReq.builder()
                .collectionName(collectionName(knowledgeBaseId))
                .filter("documentId == " + documentId)
                .build());
    }

    @Override
    public void deleteByVectorId(Long knowledgeBaseId, String vectorId) {
        if (!collectionExists(knowledgeBaseId)) {
            return;
        }
        client.delete(DeleteReq.builder()
                .collectionName(collectionName(knowledgeBaseId))
                .filter("vector_id == '" + escape(vectorId) + "'")
                .build());
    }

    @Override
    public List<RetrievalHit> search(List<Long> knowledgeBaseIds, List<Float> queryVector, int topK) {
        var hits = new ArrayList<RetrievalHit>();
        for (Long knowledgeBaseId : knowledgeBaseIds) {
            try {
                // 集合可能在索引后被 Milvus 重启释放：检索前确保已载入内存（load 幂等，已载入为 no-op）。
                // ponytail: load 是异步的，重启后首次检索仍可能报 not loaded，随后一次即恢复；若需强一致再加 load-state 轮询。
                if (collectionExists(knowledgeBaseId)) {
                    loadCollection(knowledgeBaseId);
                }
                SearchResp resp = client.search(SearchReq.builder()
                        .collectionName(collectionName(knowledgeBaseId))
                        .data(List.of(new FloatVec(queryVector)))
                        .annsField("vector")
                        .topK(topK)
                        .outputFields(List.of("vector_id", "documentId", "chunkIndex"))
                        .build());
                if (resp.getSearchResults() == null) {
                    continue;
                }
                for (List<SearchResp.SearchResult> perQuery : resp.getSearchResults()) {
                    for (SearchResp.SearchResult result : perQuery) {
                        hits.add(toHit(knowledgeBaseId, result));
                    }
                }
            } catch (Exception exception) {
                // 集合不存在或后端不可用：该知识库降级为空，继续其余知识库（记录原因便于排查）
                log.warn("Milvus 检索降级为空: kb={}, reason={}", knowledgeBaseId, exception.getMessage());
            }
        }
        hits.sort(Comparator.comparingDouble(RetrievalHit::score).reversed());
        return hits.size() > topK ? hits.subList(0, topK) : hits;
    }

    /**
     * 幂等建 collection 并建向量索引：显式 schema 以业务 {@code vector_id} 为主键（关 autoID），
     * 动态字段承载 documentId/chunkIndex/content 等定位信息。
     */
    private void ensureCollection(Long knowledgeBaseId, int dimension) {
        String name = collectionName(knowledgeBaseId);
        if (Boolean.TRUE.equals(client.hasCollection(HasCollectionReq.builder().collectionName(name).build()))) {
            ensureIndex(name);
            return;
        }
        var vectorField = AddFieldReq.builder()
                .fieldName("vector")
                .dataType(DataType.FloatVector)
                .dimension(dimension)
                .build();
        var idField = AddFieldReq.builder()
                .fieldName("vector_id")
                .dataType(DataType.VarChar)
                .isPrimaryKey(true)
                .maxLength(128)
                .build();
        var docField = AddFieldReq.builder()
                .fieldName("documentId")
                .dataType(DataType.Int64)
                .build();
        var chunkField = AddFieldReq.builder()
                .fieldName("chunkIndex")
                .dataType(DataType.Int64)
                .build();
        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                .enableDynamicField(true)
                .build()
                .addField(idField)
                .addField(vectorField)
                .addField(docField)
                .addField(chunkField);
        client.createCollection(CreateCollectionReq.builder()
                .collectionName(name)
                .metricType(config.getMetricType() == null ? "COSINE" : config.getMetricType())
                .collectionSchema(schema)
                .build());
        ensureIndex(name);
    }

    /**
     * 幂等建向量索引：已存在索引则跳过；未实现的索引类型回退 AUTOINDEX（维度无关、零参数）。
     */
    private void ensureIndex(String collectionName) {
        var existing = client.listIndexes(ListIndexesReq.builder().collectionName(collectionName).build());
        if (existing != null && !existing.isEmpty()) {
            return;
        }
        IndexParam.IndexType indexType = indexType();
        client.createIndex(CreateIndexReq.builder()
                .collectionName(collectionName)
                .indexParams(List.of(IndexParam.builder()
                        .fieldName("vector")
                        .indexName("vector_idx")
                        .indexType(indexType)
                        .metricType(metricType())
                        .build()))
                .build());
    }

    private void loadCollection(Long knowledgeBaseId) {
        client.loadCollection(LoadCollectionReq.builder()
                .collectionName(collectionName(knowledgeBaseId))
                .build());
    }

    private boolean collectionExists(Long knowledgeBaseId) {
        return Boolean.TRUE.equals(client.hasCollection(
                HasCollectionReq.builder().collectionName(collectionName(knowledgeBaseId)).build()));
    }

    private IndexParam.IndexType indexType() {
        String configured = config.getIndexType();
        if (configured == null || "AUTOINDEX".equalsIgnoreCase(configured)) {
            return IndexParam.IndexType.AUTOINDEX;
        }
        log.warn("Milvus 索引类型 {} 尚未实现，回退 AUTOINDEX", configured);
        return IndexParam.IndexType.AUTOINDEX;
    }

    private IndexParam.MetricType metricType() {
        if ("L2".equalsIgnoreCase(config.getMetricType())) {
            return IndexParam.MetricType.L2;
        }
        if ("IP".equalsIgnoreCase(config.getMetricType())) {
            return IndexParam.MetricType.IP;
        }
        return IndexParam.MetricType.COSINE;
    }

    private JsonObject rowJson(VectorChunk chunk) {
        JsonArray vector = new JsonArray();
        for (Float f : chunk.embedding()) {
            vector.add(f);
        }
        JsonObject row = new JsonObject();
        row.addProperty("vector_id", chunk.vectorId());
        row.add("vector", vector);
        row.addProperty("documentId", chunk.documentId());
        row.addProperty("chunkIndex", chunk.chunkIndex());
        return row;
    }

    private RetrievalHit toHit(Long knowledgeBaseId, SearchResp.SearchResult result) {
        Map<String, Object> entity = result.getEntity();
        double score = result.getScore() == null ? 0.0 : result.getScore();
        return new RetrievalHit(
                entity.get("vector_id") == null ? null : String.valueOf(entity.get("vector_id")),
                knowledgeBaseId,
                number(entity.get("documentId")).longValue(),
                number(entity.get("chunkIndex")).intValue(),
                null,
                null,
                score);
    }

    private static Number number(Object value) {
        return value instanceof Number number ? number : 0;
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("'", "\\'");
    }

    public void close() {
        client.close();
    }

    private String collectionName(Long knowledgeBaseId) {
        return config.getCollectionPrefix() + "_" + knowledgeBaseId;
    }
}