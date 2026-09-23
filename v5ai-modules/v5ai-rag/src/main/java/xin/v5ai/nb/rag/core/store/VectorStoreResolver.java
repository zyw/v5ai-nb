package xin.v5ai.nb.rag.core.store;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.common.elasticsearch.ElasticsearchClientFactory;
import xin.v5ai.nb.common.elasticsearch.domain.ElasticsearchVectorConfigDO;
import xin.v5ai.nb.common.milvus.MilvusClientFactory;
import xin.v5ai.nb.common.milvus.domain.MilvusVectorConfigDO;
import xin.v5ai.nb.common.core.exception.ServiceException;
import xin.v5ai.nb.rag.core.config.PgVectorConfigDO;
import xin.v5ai.nb.rag.domain.StoreInstance;
import xin.v5ai.nb.rag.mapper.StoreInstanceMapper;
import xin.v5ai.nb.rag.service.IKnowledgeChunkService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按存储实例类型解析 {@link VectorStore} / {@link KeywordStore}：所有外部存储均按实例 config
 * 构建并缓存客户端；实例更新/删除时失效缓存并关闭连接。
 */
@Component
@RequiredArgsConstructor
public class VectorStoreResolver {

    private static final int TYPE_PG_VECTOR = 1;
    private static final int TYPE_MILVUS = 2;
    private static final int TYPE_ELASTICSEARCH = 3;
    /**
     * 业务库原生关键词检索（历史名 PG_FULLTEXT）：只实现 {@link KeywordStore}，不连外部服务。
     */
    private static final int TYPE_DB_FULLTEXT = 4;

    private final StoreInstanceMapper storeInstanceMapper;
    private final IKnowledgeChunkService chunkService;
    private final PgVectorDataSourceFactory pgDataSourceFactory;
    private final PgBm25KeywordSearch pgBm25KeywordSearch;

    private final Map<Long, Object> resolved = new ConcurrentHashMap<>();

    public VectorStore vectorStore(Long instanceId) {
        if (instanceId == null) {
            throw new ServiceException("知识库未绑定向量存储实例，无法进行向量检索/写入");
        }
        Object store = resolve(instanceId);
        if (store instanceof VectorStore vectorStore) {
            return vectorStore;
        }
        throw new ServiceException("该存储实例类型不支持向量检索: " + instanceId);
    }

    /**
     * 返回关键词后端；实例本身不支持关键词（只有 Milvus——它只承载向量）时返回 null，
     * 调用方跳过关键词这一路。PG_VECTOR / ELASTICSEARCH / DB_FULLTEXT 都有关键词能力。
     */
    public KeywordStore keywordStore(Long instanceId) {
        if (instanceId == null) {
            return null;
        }
        Object store = resolve(instanceId);
        return store instanceof KeywordStore keywordStore ? keywordStore : null;
    }

    public void invalidate(Long instanceId) {
        Object removed = resolved.remove(instanceId);
        if (removed instanceof ElasticsearchVectorStore es) {
            es.close();
        } else if (removed instanceof MilvusVectorStore milvus) {
            milvus.close();
        } else if (removed instanceof PgVectorStore pg) {
            pg.close();
        }
    }

    private Object resolve(Long instanceId) {
        StoreInstance instance = storeInstanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new ServiceException("存储实例不存在: " + instanceId);
        }
        Integer type = instance.getType();
        if (type == null) {
            throw new ServiceException("存储实例缺少类型: " + instanceId);
        }
        if (type != TYPE_PG_VECTOR && type != TYPE_MILVUS && type != TYPE_ELASTICSEARCH && type != TYPE_DB_FULLTEXT) {
            // 尚未接线的类型：无实现返回 null，由调用方跳过或报错
            return null;
        }
        return resolved.computeIfAbsent(instanceId, id -> build(instance));
    }

    private Object build(StoreInstance instance) {
        return switch (instance.getType()) {
            case TYPE_MILVUS -> {
                var config = toConfig(instance.getConfig(), MilvusVectorConfigDO.class);
                yield new MilvusVectorStore(new MilvusClientFactory().create(config), config);
            }
            case TYPE_ELASTICSEARCH -> {
                var config = toConfig(instance.getConfig(), ElasticsearchVectorConfigDO.class);
                yield new ElasticsearchVectorStore(new ElasticsearchClientFactory().create(config), config);
            }
            case TYPE_PG_VECTOR -> {
                var config = toConfig(instance.getConfig(), PgVectorConfigDO.class);
                yield new PgVectorStore(pgDataSourceFactory.create(config), chunkService, pgBm25KeywordSearch);
            }
            // 不读 config：分词就在业务库的切片行上，实例只是「按业务库做 BM25」的开关与归属标识
            case TYPE_DB_FULLTEXT -> new DbNativeKeywordStore(pgBm25KeywordSearch);
            default -> null;
        };
    }

    private static <T> T toConfig(String configJson, Class<T> type) {
        if (StrUtil.isBlank(configJson)) {
            try {
                return type.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("实例化配置对象失败: " + type.getName(), e);
            }
        }
        return JSONUtil.toBean(configJson, type);
    }
}
