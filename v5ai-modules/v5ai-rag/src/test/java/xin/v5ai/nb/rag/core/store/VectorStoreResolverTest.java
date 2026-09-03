package xin.v5ai.nb.rag.core.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.core.exception.ServiceException;
import xin.v5ai.nb.rag.domain.StoreInstance;
import xin.v5ai.nb.rag.mapper.StoreInstanceMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link VectorStoreResolver} 分发/缓存/失效的单测：type1 回主数据源 PG 单例，
 * type3 构建并缓存 ES 存储，type4 未接线返回 null / 向量检索报错，invalidate 关闭缓存。
 */
class VectorStoreResolverTest {

    private final StoreInstanceMapper mapper = mock(StoreInstanceMapper.class);
    private final PgVectorStore pgVectorStore = mock(PgVectorStore.class);
    private VectorStoreResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new VectorStoreResolver(mapper, pgVectorStore);
    }

    @Test
    void nullInstanceIdDefaultsToPgVectorStore() {
        assertThat(resolver.vectorStore(null)).isSameAs(pgVectorStore);
    }

    @Test
    void pgVectorTypeReturnsPgSingleton() {
        when(mapper.selectById(1L)).thenReturn(instance(1L, 1));

        assertThat(resolver.vectorStore(1L)).isSameAs(pgVectorStore);
        assertThat(resolver.keywordStore(1L)).isSameAs(pgVectorStore);
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
    void pgFulltextTypeKeywordReturnsNullAndVectorThrows() {
        when(mapper.selectById(4L)).thenReturn(instance(4L, 4));

        assertThat(resolver.keywordStore(4L)).isNull();
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
