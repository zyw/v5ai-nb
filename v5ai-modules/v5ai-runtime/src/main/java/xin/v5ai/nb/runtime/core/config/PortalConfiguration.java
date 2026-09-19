package xin.v5ai.nb.runtime.core.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import xin.v5ai.nb.runtime.core.config.properties.ConversationTitleProperties;
import xin.v5ai.nb.runtime.core.config.properties.PortalAttachmentProperties;

/**
 * 对话门户运行配置：门户附件参数 {@link PortalAttachmentProperties}
 * 与会话标题参数 {@link ConversationTitleProperties}。
 */
@Configuration
@EnableConfigurationProperties({PortalAttachmentProperties.class, ConversationTitleProperties.class})
public class PortalConfiguration {
}
