package xin.v5ai.nb.common.elasticsearch.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xin.v5ai.nb.common.elasticsearch.ElasticsearchConnectionTester;

@Configuration
public class ElasticSearchConfig {

    @Bean
    public ElasticsearchConnectionTester elasticSearchConnectionTester() {
        return new ElasticsearchConnectionTester();
    }
}
