package xin.v5ai.nb.rag.core;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.rag.core.config.PgVectorConfigDO;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link StoreConnectionTester} 的 URL 拼装单测（真实 JDBC 连通性需集成环境，不在单测范围）。
 */
class StoreConnectionTesterTest {

    @Test
    void buildJdbcUrlContainsHostPortDatabaseAndTimeouts() {
        var config = new PgVectorConfigDO();
        config.setHost("10.0.0.8");
        config.setPort(5433);
        config.setDatabase("v5ai_nb");

        String url = StoreConnectionTester.buildJdbcUrl(config, 5);

        assertThat(url).isEqualTo("jdbc:postgresql://10.0.0.8:5433/v5ai_nb?connectTimeout=5&loginTimeout=5");
    }

    @Test
    void buildJdbcUrlAppendsSslModeWhenEnabled() {
        var config = new PgVectorConfigDO();
        config.setSslEnabled(true);
        config.setSslMode("verify-full");

        String url = StoreConnectionTester.buildJdbcUrl(config, 3);

        assertThat(url).endsWith("&sslmode=verify-full");
    }

    @Test
    void buildJdbcUrlDefaultsSslModeWhenBlank() {
        var config = new PgVectorConfigDO();
        config.setSslEnabled(true);
        config.setSslMode("");

        String url = StoreConnectionTester.buildJdbcUrl(config, 3);

        assertThat(url).endsWith("&sslmode=require");
    }
}
