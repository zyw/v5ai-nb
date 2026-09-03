package xin.v5ai.nb.rag.core.store;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import xin.v5ai.nb.common.elasticsearch.domain.ElasticsearchVectorConfigDO;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Elasticsearch 向量/关键词存储：向量走 kNN、关键词走 match，同一索引按知识库隔离
 * （索引名 {@code {indexPrefix}_{knowledgeBaseId}}），首次写入时惰性创建索引与 mapping。
 */
public class ElasticsearchVectorStore implements VectorStore, KeywordStore {

    private final RestClient client;
    private final ElasticsearchVectorConfigDO config;

    public ElasticsearchVectorStore(RestClient client, ElasticsearchVectorConfigDO config) {
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
        ensureIndex(knowledgeBaseId, dimension);

        var bulk = new StringBuilder();
        for (VectorChunk chunk : chunks) {
            bulk.append("{\"index\":{\"_index\":\"").append(indexName(knowledgeBaseId)).append("\"}}\n");
            bulk.append(docJson(chunk)).append('\n');
        }
        Request request = new Request("POST", "/_bulk");
        request.setEntity(new StringEntity(bulk.toString(), ContentType.create("application/x-ndjson", StandardCharsets.UTF_8)));
        Response response = perform(request);
        JSONObject body = JSONUtil.parseObj(entity(response));
        if (body.getBool("errors", false)) {
            throw new IllegalStateException("ES bulk 写入失败: " + body);
        }
    }

    @Override
    public void deleteByDocumentId(Long knowledgeBaseId, Long documentId) {
        JSONObject query = new JSONObject().set("query", new JSONObject().set("term",
                new JSONObject().set("documentId", documentId)));
        Request request = new Request("POST", "/" + indexName(knowledgeBaseId) + "/_delete_by_query");
        request.setJsonEntity(query.toString());
        perform(request);
    }

    @Override
    public List<RetrievalHit> search(List<Long> knowledgeBaseIds, List<Float> queryVector, int topK) {
        var vector = new JSONArray();
        queryVector.forEach(vector::add);
        var filter = new JSONObject().set("terms",
                new JSONObject().set("knowledgeBaseId", new JSONArray(knowledgeBaseIds)));
        var knn = new JSONObject().set("vector", vector)
                .set("k", topK)
                .set("num_candidates", Math.max(config.getNumCandidates(), topK))
                .set("filter", filter);
        var body = new JSONObject().set("query", new JSONObject().set("knn", new JSONObject().set("embedding", knn)));
        return searchHits(body);
    }

    @Override
    public List<RetrievalHit> keywordSearch(List<Long> knowledgeBaseIds, String keyword, int topK) {
        var must = new JSONArray();
        must.add(new JSONObject().set("match", new JSONObject().set("content", keyword)));
        must.add(new JSONObject().set("terms", new JSONObject().set("knowledgeBaseId", new JSONArray(knowledgeBaseIds))));
        var body = new JSONObject()
                .set("query", new JSONObject().set("bool", new JSONObject().set("must", must)))
                .set("size", topK);
        return searchHits(body);
    }

    private List<RetrievalHit> searchHits(JSONObject queryBody) {
        Request request = new Request("POST", "/" + config.getIndexPrefix() + "_*/_search");
        request.setJsonEntity(queryBody.toString());
        JSONObject body = JSONUtil.parseObj(entity(perform(request)));
        JSONArray hits = body.getJSONObject("hits").getJSONArray("hits");
        var result = new ArrayList<RetrievalHit>(hits.size());
        for (int i = 0; i < hits.size(); i++) {
            JSONObject hit = hits.getJSONObject(i);
            JSONObject source = hit.getJSONObject("_source");
            result.add(new RetrievalHit(
                    source.getLong("knowledgeBaseId"),
                    source.getLong("documentId"),
                    source.getInt("chunkIndex", 0),
                    source.getStr("content"),
                    source.getStr("metadata"),
                    hit.getDouble("_score", 1.0)));
        }
        return result;
    }

    private void ensureIndex(Long knowledgeBaseId, int dimension) {
        String index = indexName(knowledgeBaseId);
        int status;
        try {
            status = client.performRequest(new Request("HEAD", "/" + index)).getStatusLine().getStatusCode();
        } catch (ResponseException e) {
            status = e.getResponse().getStatusLine().getStatusCode();
        } catch (IOException e) {
            throw new IllegalStateException("检查 ES 索引失败: " + index, e);
        }
        if (status == 404) {
            Request put = new Request("PUT", "/" + index);
            put.setJsonEntity(mappingJson(dimension).toString());
            perform(put);
        } else if (status >= 300) {
            throw new IllegalStateException("ES 索引检查失败: " + index + " 状态码 " + status);
        }
    }

    private JSONObject mappingJson(int dimension) {
        var properties = new JSONObject();
        properties.set("content", new JSONObject().set("type", "text"));
        properties.set("knowledgeBaseId", new JSONObject().set("type", "long"));
        properties.set("documentId", new JSONObject().set("type", "long"));
        properties.set("chunkIndex", new JSONObject().set("type", "integer"));
        properties.set("metadata", new JSONObject().set("type", "text").set("index", false));
        properties.set("embedding", new JSONObject()
                .set("type", "dense_vector")
                .set("dims", dimension)
                .set("index", true)
                .set("similarity", config.getSimilarity() == null ? "cosine" : config.getSimilarity()));
        return new JSONObject().set("mappings", new JSONObject().set("properties", properties));
    }

    private JSONObject docJson(VectorChunk chunk) {
        var vector = new JSONArray();
        for (Float f : chunk.embedding()) {
            vector.add(f);
        }
        return new JSONObject()
                .set("knowledgeBaseId", chunk.knowledgeBaseId())
                .set("documentId", chunk.documentId())
                .set("chunkIndex", chunk.chunkIndex())
                .set("content", chunk.content())
                .set("metadata", chunk.metadata() == null ? "" : chunk.metadata())
                .set("embedding", vector);
    }

    public void close() {
        try {
            client.close();
        } catch (IOException e) {
            throw new IllegalStateException("关闭 Elasticsearch 客户端失败", e);
        }
    }

    private String indexName(Long knowledgeBaseId) {
        return config.getIndexPrefix() + "_" + knowledgeBaseId;
    }

    private Response perform(Request request) {
        try {
            return client.performRequest(request);
        } catch (IOException e) {
            throw new IllegalStateException("Elasticsearch 请求失败: " + request.getMethod() + " " + request.getEndpoint(), e);
        }
    }

    private static String entity(Response response) {
        try {
            return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取 Elasticsearch 响应失败", e);
        }
    }
}
