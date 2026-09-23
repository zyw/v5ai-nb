package xin.v5ai.nb.runtime.core.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 会话标题（模型改写）相关的运行参数。
 *
 * <p>标题是**可选覆盖**：会话创建时已经用首条提问写了兜底名（{@code ConversationNaming.fromQuery}），
 * 这里的开关与超时只决定要不要再花一次小模型调用把名字改好看。</p>
 */
@Data
@ConfigurationProperties(prefix = "v5ai.chat.conversation-title")
public class ConversationTitleProperties {

    /**
     * 是否启用模型改写标题；关闭后退化为纯兜底名（列表与改名都不受影响）。
     */
    private boolean enabled = true;

    /**
     * 单次标题生成的超时（毫秒）：超时即放弃，保留兜底名。
     */
    private long timeoutMillis = 5000;

    /**
     * 标题长度上限（码点）：比兜底名短得多，列表里一行放得下。
     */
    private int maxLength = 30;
}
