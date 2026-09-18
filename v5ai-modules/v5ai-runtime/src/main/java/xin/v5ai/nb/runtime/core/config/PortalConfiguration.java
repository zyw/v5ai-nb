package xin.v5ai.nb.runtime.core.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import xin.v5ai.nb.runtime.core.config.properties.PortalAttachmentProperties;

/**
 * 对话门户运行配置：目前只登记门户附件参数 {@link PortalAttachmentProperties}。
 */
@Configuration
@EnableConfigurationProperties(PortalAttachmentProperties.class)
public class PortalConfiguration {
}
