package xin.v5ai.nb.runtime.core.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import xin.v5ai.nb.common.agentscope.core.history.HistoryWindow;

/**
 * 同会话历史窗口（{@code v5ai.agentscope.history}）：决定一次运行最多把多少历史发给模型。
 *
 * <p>超窗的旧内容不再直接丢弃，而是由会话摘要（见 {@code ConversationSummaryWriter}）以
 * 「更早内容的摘要」形式继续参与上下文。图片不占这里的字符预算——它有独立的 16MB 字节预算
 * （{@code v5ai.chat.attachment.history-budget-bytes}）。</p>
 */
@Data
@ConfigurationProperties(prefix = "v5ai.agentscope.history")
public class HistoryWindowProperties {

    /**
     * 是否启用窗口裁剪。关闭时该会话全部未作废消息都会被发给模型——长会话会顶爆模型窗口，
     * 只建议在排查问题时临时关闭。
     */
    private boolean enabled = true;

    /**
     * 保留的历史条数上限（含首尾），默认 40。
     */
    private int maxMessages = 40;

    /**
     * 保留的历史正文字符上限（不含图片字节），默认 8000。
     */
    private int maxChars = 8000;

    /**
     * 保底条数：预算再紧也要保留的最近消息条数，默认 2。
     */
    private int minMessages = 2;

    /**
     * 裁剪后首条若是助手回答，继续往前丢，避免出现「没有提问的回答」，默认开启。
     */
    private boolean alignToUserTurn = true;

    /**
     * 转成运行时用的纯函数窗口。
     */
    public HistoryWindow toWindow() {
        return enabled
                ? new HistoryWindow(maxMessages, maxChars, minMessages, alignToUserTurn)
                : HistoryWindow.unlimited();
    }
}
