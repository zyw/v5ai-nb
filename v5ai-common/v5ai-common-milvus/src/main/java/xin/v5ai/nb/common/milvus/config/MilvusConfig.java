package xin.v5ai.nb.common.milvus.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xin.v5ai.nb.common.milvus.MilvusConnectionTester;

@Configuration
public class MilvusConfig {

    @Bean
    MilvusConnectionTester milvusConnectionTester() {
        return new MilvusConnectionTester();
    }
}
