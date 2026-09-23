package xin.v5ai.nb.common.web.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.LocaleResolver;
import xin.v5ai.nb.common.web.core.I18nLocaleResolver;

/**
 * 国际化配置
 *
 * @author Lion Li
 */
@AutoConfiguration(before = WebMvcAutoConfiguration.class)
public class I18nConfig {

    /**
     * 注册自定义国际化区域解析器。
     *
     * @return Locale 解析器实例
     */
    @Bean
    public LocaleResolver localeResolver() {
        return new I18nLocaleResolver();
    }

}
