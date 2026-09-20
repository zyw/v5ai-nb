package xin.v5ai.nb.runtime.core.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对话门户附件相关的运行参数。
 *
 * <p>体积与预算是刻意的取舍（见 v5ai-nb#6 Q19/Q21）：单张 5MB、每条消息最多 3 张，
 * 历史回放累计 16MB，超出部分退化为 {@code [图片]} 文字占位——更早的图片会从上下文里消失。</p>
 */
@Data
@ConfigurationProperties(prefix = "v5ai.chat.attachment")
public class ChatAttachmentProperties {

    /**
     * 单张附件的体积上限（字节），默认 5MB。
     */
    private long maxFileSizeBytes = 5L * 1024 * 1024;

    /**
     * 每把 API Key 每分钟允许的上传次数（滑动窗口，进程内）。
     */
    private int uploadsPerMinute = 20;

    /**
     * 每条消息允许携带的新图数量上限。
     */
    private int maxPerMessage = 3;

    /**
     * 历史回放中图片的累计体积预算（字节），默认 16MB。
     */
    private long historyBudgetBytes = 16L * 1024 * 1024;
}
