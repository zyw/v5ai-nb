package xin.v5ai.nb.rag.core;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.rag.core.config.PgVectorConfigDO;
import xin.v5ai.nb.rag.domain.vo.StoreConnectionTestVo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 存储实例连接测试：按 {@code type + config} 直接建立一次 JDBC 连接验证 PostgreSQL 连通性。
 * PG_VECTOR 额外校验 pgvector 扩展可用；Milvus / Elasticsearch 无运行时实现，不支持。
 */
@Component
public class StoreConnectionTester {

    private static final int TYPE_PG_VECTOR = 1;
    private static final int TYPE_PG_FULLTEXT = 4;

    private final int timeoutSeconds;

    public StoreConnectionTester(@Value("${store.connection-test.timeout-seconds:5}") int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public StoreConnectionTestVo test(Integer type, String configJson) {
        if (StrUtil.isBlank(configJson) || !JSONUtil.isTypeJSON(configJson)) {
            return StoreConnectionTestVo.failure("连接参数必须为合法 JSON");
        }
        PgVectorConfigDO config;
        try {
            config = JSONUtil.toBean(configJson, PgVectorConfigDO.class);
        } catch (Exception e) {
            return StoreConnectionTestVo.failure("连接参数解析失败: " + messageOf(e));
        }
        String url = buildJdbcUrl(config, timeoutSeconds);
        try (Connection connection = DriverManager.getConnection(url, config.getUsername(), config.getPassword())) {
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT 1")) {
                rs.next();
            }
            if (type != null && type == TYPE_PG_VECTOR && !hasPgVector(connection)) {
                return StoreConnectionTestVo.failure("pgvector 扩展未安装（数据库 " + config.getDatabase() + "）");
            }
            return type != null && type == TYPE_PG_VECTOR
                    ? StoreConnectionTestVo.success("连接成功，pgvector 扩展可用")
                    : StoreConnectionTestVo.success("连接成功");
        } catch (SQLException e) {
            String target = config.getHost() + ":" + config.getPort() + "/" + config.getDatabase();
            return StoreConnectionTestVo.failure("连接 PostgreSQL(" + target + ") 失败: " + messageOf(e));
        }
    }

    private boolean hasPgVector(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT 1 FROM pg_extension WHERE extname = 'vector'")) {
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
