package xin.v5ai.nb.infrastructure.redis;

import org.junit.jupiter.api.Test;
import org.redisson.config.Config;

import static org.assertj.core.api.Assertions.assertThat;

class RedissonConfigurationTest {
    @Test
    void createsSingleServerConfigFromProperties() {
        var properties = new RedissonProperties();
        properties.setAddress("redis://localhost:6379");
        properties.setDatabase(2);

        Config config = RedissonConfiguration.createConfig(properties);

        assertThat(config.useSingleServer().getAddress()).isEqualTo("redis://localhost:6379");
        assertThat(config.useSingleServer().getDatabase()).isEqualTo(2);
    }

    @Test
    void blankPasswordIsNotApplied() {
        var properties = new RedissonProperties();
        properties.setAddress("redis://localhost:6379");
        properties.setPassword("");

        Config config = RedissonConfiguration.createConfig(properties);

        assertThat(config.useSingleServer().getPassword()).isNull();
    }
}
