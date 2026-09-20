package xin.v5ai.nb.runtime.core.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import xin.v5ai.nb.runtime.core.config.properties.ConversationSummaryProperties;
import xin.v5ai.nb.runtime.core.config.properties.ConversationTitleProperties;
import xin.v5ai.nb.runtime.core.config.properties.ChatAttachmentProperties;

/**
 * 对话门户运行配置：门户附件参数 {@link ChatAttachmentProperties}、
 * 会话标题参数 {@link ConversationTitleProperties} 与会话摘要参数 {@link ConversationSummaryProperties}。
 */
@Configuration
@EnableConfigurationProperties({ChatAttachmentProperties.class, ConversationTitleProperties.class,
        ConversationSummaryProperties.class})
public class ChatConfiguration {
}
