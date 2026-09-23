package xin.v5ai.nb.rag.core;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.common.elasticsearch.ElasticsearchConnectionTester;
import xin.v5ai.nb.common.elasticsearch.domain.ElasticsearchVectorConfigDO;
import xin.v5ai.nb.common.milvus.MilvusConnectionTester;
import xin.v5ai.nb.common.milvus.domain.MilvusVectorConfigDO;
import xin.v5ai.nb.rag.core.config.PgVectorConfigDO;
import xin.v5ai.nb.rag.domain.vo.StoreConnectionTestVo;

import java.sql.*;

/**
 * 存储实例连接测试门面：按 {@code type} 分发到 PG(1) JDBC、Milvus(2) listCollections、
 * Elasticsearch(3) GET /；DB_FULLTEXT(4) 用应用自己的业务库、无外部连接可测，直接返回可用。
 */
@Component
@RequiredArgsConstructor
public class StoreConnectionTester {

    private static final int TYPE_PG_VECTOR = 1;
    private static final int TYPE_MILVUS = 2;
    private static final int TYPE_ELASTICSEARCH = 3;
    private static final int TYPE_DB_FULLTEXT = 4;

    private static final String SQL_VECTOR = "SELECT 1 FROM pg_extension WHERE extname = 'vector'";
    private static final String SQL_ONE = "SELECT 1";

    @Value("${v5ai.store.connection-test.timeout-seconds:5}")
    private int timeoutSeconds;

    private final MilvusConnectionTester milvusTester;
    private final ElasticsearchConnectionTester esTester;

    public StoreConnectionTestVo test(Integer type, String configJson) {
        if (type == null) {
            return StoreConnectionTestVo.failure("存储实例缺少类型");
        }
        if (type == TYPE_DB_FULLTEXT) {
            // 分词存在业务库的切片行上，没有外部服务可连、也不需要连接参数；
            // 能否真的检索由启动时的业务库方言决定（见 docs/adr/0012），这里不做假探测。
            return StoreConnectionTestVo.success("无需连接：使用应用自身的业务库（PostgreSQL / MySQL）做 BM25");
        }
        if (StrUtil.isBlank(configJson) || !JSONUtil.isTypeJSON(configJson)) {
            return StoreConnectionTestVo.failure("连接参数必须为合法 JSON");
        }
        try {
            return switch (type) {
                case TYPE_MILVUS -> {
                    var result = milvusTester.test(JSONUtil.toBean(configJson, MilvusVectorConfigDO.class));
                    yield result.ok() ? StoreConnectionTestVo.success(result.message())
                            : StoreConnectionTestVo.failure(result.message());
                }
                case TYPE_ELASTICSEARCH -> {
                    var result = esTester.test(JSONUtil.toBean(configJson, ElasticsearchVectorConfigDO.class));
                    yield result.ok() ? StoreConnectionTestVo.success(result.message())
                            : StoreConnectionTestVo.failure(result.message());
                }
                case TYPE_PG_VECTOR -> testPg(JSONUtil.toBean(configJson, PgVectorConfigDO.class));
                default -> StoreConnectionTestVo.failure("不支持的存储实例类型: " + type);
            };
        } catch (Exception e) {
            return StoreConnectionTestVo.failure("连接参数解析失败: " + messageOf(e));
        }
    }

    /**
     * PG_VECTOR 实例的连接测试：连通后必须确认 pgvector 扩展在位——
     * 向量集合的建表与 {@code <=>} 检索都依赖它，缺扩展的库连得上也用不了。
     */
    private StoreConnectionTestVo testPg(PgVectorConfigDO config) {
        String url = buildJdbcUrl(config, timeoutSeconds);
        try (Connection connection = DriverManager.getConnection(url, config.getUsername(), config.getPassword())) {
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(SQL_ONE)) {
                rs.next();
            }
            if (!hasPgVector(connection)) {
                return StoreConnectionTestVo.failure("pgvector 扩展未安装（数据库 " + config.getDatabase() + "）");
            }
            return StoreConnectionTestVo.success("连接成功，pgvector 扩展可用");
        } catch (SQLException e) {
            String target = config.getHost() + ":" + config.getPort() + "/" + config.getDatabase();
            return StoreConnectionTestVo.failure("连接 PostgreSQL(" + target + ") 失败: " + messageOf(e));
        }
    }

    private boolean hasPgVector(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(SQL_VECTOR)) {
            return rs.next();
        }
    }

    /**
     * 组装 PostgreSQL JDBC URL（含连接/登录超时与 sslmode），抽成静态方法便于单测。
     */
    static String buildJdbcUrl(PgVectorConfigDO config, int timeoutSeconds) {
        String url = "jdbc:postgresql://" + config.getHost() + ":" + config.getPort() + "/" + config.getDatabase()
                + "?connectTimeout=" + timeoutSeconds + "&loginTimeout=" + timeoutSeconds;
        if (config.isSslEnabled()) {
            url += "&sslmode=" + (StrUtil.isBlank(config.getSslMode()) ? "require" : config.getSslMode());
        }
        return url;
    }

    private static String messageOf(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
