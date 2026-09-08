package xin.v5ai.nb.rag.core.store;

import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import xin.v5ai.nb.common.milvus.domain.MilvusVectorConfigDO;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MilvusVectorStore} 的 Mockito 单测：mock {@link MilvusClientV2}，
 * 覆盖「删除不存在的 collection 幂等跳过」（根因回归）、首次写入建 collection/索引/载入的时序，
 * 以及未实现索引类型的 AUTOINDEX 回退。
 */
class MilvusVectorStoreTest {

    private MilvusClientV2 client;
    private MilvusVectorStore store;

    @BeforeEach
    void setUp() {
        client = mock(MilvusClientV2.class);
        store = new MilvusVectorStore(client, config());
    }

    private static MilvusVectorConfigDO config() {
        var config = new MilvusVectorConfigDO();
        config.setCollectionPrefix("v5ai_rag_vector");
        config.setIndexType("AUTOINDEX");
        config.setMetricType("COSINE");
        return config;
    }

    private static VectorStore.VectorChunk chunk() {
        return new VectorStore.VectorChunk("v1", 2L, 10L, 0, "text",
                List.of(1.0f, 2.0f), "{}", 1, "hash", "TEXT");
    }

    @Test
    void deleteByDocumentIdSkipsWhenCollectionMissing() {
        when(client.hasCollection(any())).thenReturn(false);

        store.deleteByDocumentId(2L, 10L);

        verify(client, never()).delete(any(DeleteReq.class));
    }

    @Test
    void deleteByVectorIdSkipsWhenCollectionMissing() {
        when(client.hasCollection(any())).thenReturn(false);

        store.deleteByVectorId(2L, "v1");

        verify(client, never()).delete(any(DeleteReq.class));
    }

    @Test
    void deleteByDocumentIdDeletesWhenCollectionExists() {
        when(client.hasCollection(any())).thenReturn(true);

        store.deleteByDocumentId(2L, 10L);

        verify(client).delete(any(DeleteReq.class));
    }

    @Test
    void firstStoreCreatesCollectionIndexAndLoads() {
        when(client.hasCollection(any())).thenReturn(false);
        when(client.listIndexes(any())).thenReturn(List.of());

        store.store(List.of(chunk()));

        verify(client).createCollection(any());
        verify(client).createIndex(any(CreateIndexReq.class));
        verify(client).insert(any());
        verify(client).loadCollection(any(LoadCollectionReq.class));
    }

    @Test
    void storeSkipsCreateWhenCollectionAndIndexExist() {
        when(client.hasCollection(any())).thenReturn(true);
        when(client.listIndexes(any())).thenReturn(List.of("vector_idx"));

        store.store(List.of(chunk()));

        verify(client, never()).createCollection(any());
        verify(client, never()).createIndex(any(CreateIndexReq.class));
        verify(client).insert(any());
        verify(client).loadCollection(any(LoadCollectionReq.class));
    }

    @Test
    void unsupportedIndexTypeFallsBackToAutoIndex() {
        var config = config();
        config.setIndexType("HNSW");
        store = new MilvusVectorStore(client, config);
        when(client.hasCollection(any())).thenReturn(false);
        when(client.listIndexes(any())).thenReturn(List.of());

        store.store(List.of(chunk()));

        var captor = ArgumentCaptor.forClass(CreateIndexReq.class);
        verify(client).createIndex(captor.capture());
        assertThat(captor.getValue().getIndexParams().get(0).getIndexType())
                .isEqualTo(IndexParam.IndexType.AUTOINDEX);
    }

    @Test
    void searchDegradesToEmptyWhenBackendThrows() {
        when(client.search(any())).thenThrow(new RuntimeException("collection not loaded"));

        var hits = store.search(List.of(2L), List.of(1.0f, 2.0f), 3);

        assertThat(hits).isEmpty();
    }
}