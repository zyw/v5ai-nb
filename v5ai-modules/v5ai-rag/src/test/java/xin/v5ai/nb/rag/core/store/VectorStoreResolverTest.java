package xin.v5ai.nb.rag.core.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.core.exception.ServiceException;
import xin.v5ai.nb.rag.core.config.PgVectorConfigDO;
import xin.v5ai.nb.rag.domain.StoreInstance;
import xin.v5ai.nb.rag.mapper.StoreInstanceMapper;
import xin.v5ai.nb.rag.service.IKnowledgeChunkService;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link VectorStoreResolver} 分发/缓存/失效的单测：type1 用实例 config 建 PG 单例，
 * type3 构建并缓存 ES 存储，type4 只给关键词后端（向量检索报错），invalidate 关闭缓存。
 */
class VectorStoreResolverTest {

    private final StoreInstanceMapper mapper = mock(StoreInstanceMapper.class);
    private final IKnowledgeChunkService chunkService = mock(IKnowledgeChunkService.class);
    private final PgVectorDataSourceFactory pgDataSourceFactory = mock(PgVectorDataSourceFactory.class);
    private final PgBm25KeywordSearch pgBm25KeywordSearch = mock(PgBm25KeywordSearch.class);
    private final DataSource pgDataSource = mock(DataSource.class);
    private VectorStoreResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new VectorStoreResolver(mapper, chunkService, pgDataSourceFactory, pgBm25KeywordSearch);
        when(pgDataSourceFactory.create(org.mockito.ArgumentMatchers.any(PgVectorConfigDO.class)))
                .thenReturn(pgDataSource);
    }

    @Test
    void nullInstanceIdRejectedWithoutExplicitBinding() {
        // 无隐式默认：知识库必须显式绑定存储实例（V30 方案移除 instanceId=null → 业务库 pgvector 兜底）
        assertThatThrownBy(() -> resolver.vectorStore(null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未绑定向量存储实例");
    }

    @Test
    void pgVectorTypeUsesStoreInstanceConnectionConfigAndCachesStore() {
        StoreInstance instance = instance(1L, 1);
        instance.setConfig("{\"host\":\"pg-instance\",\"port\":55432,\"database\":\"vectors\",\"username\":\"vector_user\",\"password\":\"secret\"}");
        when(mapper.selectById(1L)).thenReturn(instance);

        VectorStore first = resolver.vectorStore(1L);
        VectorStore second = resolver.vectorStore(1L);

        assertThat(first).isInstanceOf(PgVectorStore.class);
        assertThat(first).isSameAs(second);
        assertThat(resolver.keywordStore(1L)).isSameAs(first);
        verify(pgDataSourceFactory).create(org.mockito.ArgumentMatchers.argThat(config ->
                config.getHost().equals("pg-instance")
                        && config.getPort() == 55432
                        && config.getDatabase().equals("vectors")
                        && config.getUsername().equals("vector_user")
                        && config.getPassword().equals("secret")));
    }

    @Test
    void elasticsearchTypeBuildsCachesAndServesBothPorts() {
        when(mapper.selectById(3L)).thenReturn(instance(3L, 3));

        VectorStore first = resolver.vectorStore(3L);
        VectorStore second = resolver.vectorStore(3L);

        assertThat(first).isInstanceOf(ElasticsearchVectorStore.class);
        assertThat(first).isSameAs(second);
        assertThat(resolver.keywordStore(3L)).isSameAs(first);
    }

    @Test
    void dbFulltextTypeIsKeywordOnlyAndCaches() {
        // 类型 4（历史名 PG_FULLTEXT）已接线为「业务库原生 BM25」：只有关键词能力，不读 config
        when(mapper.selectById(4L)).thenReturn(instance(4L, 4));

        var first = resolver.keywordStore(4L);
        assertThat(first).isInstanceOf(DbNativeKeywordStore.class);
        assertThat(resolver.keywordStore(4L)).isSameAs(first);
        // 不能当向量库用：向量列已不在业务库，缺向量后端必须显式报错而不是静默降级
        assertThatThrownBy(() -> resolver.vectorStore(4L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不支持向量检索");
    }

    @Test
    void missingInstanceThrows() {
        when(mapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> resolver.vectorStore(99L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("存储实例不存在");
    }

    @Test
    void invalidateClosesAndRebuilds() {
        when(mapper.selectById(3L)).thenReturn(instance(3L, 3));

        VectorStore before = resolver.vectorStore(3L);
        resolver.invalidate(3L);
        VectorStore after = resolver.vectorStore(3L);

        assertThat(after).isNotSameAs(before);
    }

    private static StoreInstance instance(Long id, Integer type) {
        var instance = new StoreInstance();
        instance.setId(id);
        instance.setName("store-" + id);
        instance.setType(type);
        instance.setCategory(type == 3 ? 2 : 1);
        instance.setConfig("{}");
        return instance;
    }
}
