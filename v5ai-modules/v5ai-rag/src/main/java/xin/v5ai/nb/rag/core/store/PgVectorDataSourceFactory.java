package xin.v5ai.nb.rag.core.store;

import cn.hutool.core.util.StrUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.rag.core.config.PgVectorConfigDO;

import javax.sql.DataSource;

/** Creates a PostgreSQL data source from a vector store instance configuration. */
@Component
public class PgVectorDataSourceFactory {

    public DataSource create(PgVectorConfigDO config) {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(buildJdbcUrl(config));
        hikari.setUsername(config.getUsername());
        hikari.setPassword(config.getPassword());
        hikari.setMaximumPoolSize(config.getMaxPoolSize());
        hikari.setMinimumIdle(config.getMinIdleConnections());
        hikari.setConnectionTimeout(config.getConnectionTimeoutMs());
        hikari.setIdleTimeout(config.getIdleTimeoutMs());
        hikari.setMaxLifetime(config.getMaxLifetimeMs());
        return new HikariDataSource(hikari);
    }

    static String buildJdbcUrl(PgVectorConfigDO config) {
        String url = "jdbc:postgresql://" + config.getHost() + ":" + config.getPort() + "/" + config.getDatabase();
        if (config.isSslEnabled()) {
            url += "?sslmode=" + (StrUtil.isBlank(config.getSslMode()) ? "require" : config.getSslMode());
        }
        return url;
    }
}
