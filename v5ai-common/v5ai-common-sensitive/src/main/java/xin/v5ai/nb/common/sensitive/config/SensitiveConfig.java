package xin.v5ai.nb.common.sensitive.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import xin.v5ai.nb.common.sensitive.handler.SensitiveJsonFieldProcessor;

/**
 * 脱敏模块配置。
 */
@AutoConfiguration
public class SensitiveConfig {

    /**
     * 创建脱敏 JSON 字段处理器。
     *
     * @return 脱敏 JSON 字段处理器
     */
    @Bean
    public SensitiveJsonFieldProcessor sensitiveJsonFieldProcessor() {
        return new SensitiveJsonFieldProcessor();
    }

}
