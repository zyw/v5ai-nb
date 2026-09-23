package xin.v5ai.nb.common.translation.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import xin.v5ai.nb.common.translation.core.TranslationInterface;
import xin.v5ai.nb.common.translation.core.handler.TranslationJsonFieldProcessor;

import java.util.List;

/**
 * 翻译模块配置类
 *
 * @author Lion Li
 */
@AutoConfiguration
public class TranslationConfig {

    /**
     * 创建翻译 JSON 字段处理器。
     *
     * @param list 翻译实现集合
     * @return 翻译 JSON 字段处理器
     */
    @Bean
    public TranslationJsonFieldProcessor translationJsonFieldProcessor(List<TranslationInterface<?>> list) {
        return new TranslationJsonFieldProcessor(list);
    }

}
