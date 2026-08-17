package xin.v5ai.nb.infrastructure.redis;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RedissonProperties.class)
public class RedissonConfiguration {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    RedissonClient redissonClient(RedissonProperties properties) {
        return Redisson.create(createConfig(properties));
    }

    static Config createConfig(RedissonProperties properties) {
        var config = new Config();
        var singleServerConfig = config.useSingleServer()
                .setAddress(properties.getAddress())
                .setDatabase(properties.getDatabase());
        if (StringUtils.hasText(properties.getPassword())) {
            singleServerConfig.setPassword(properties.getPassword());
        }
        return config;
    }
}
