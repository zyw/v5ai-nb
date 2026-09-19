package xin.v5ai.nb.common.agentscope.core.history;

import xin.v5ai.nb.common.agentscope.core.domain.SessionMessage;

import java.util.List;

/**
 * 一次历史窗口裁剪的结果（不可变）。
 *
 * <p>统计口径是「相对**本次加载到的历史**」：SQL 侧只取最近若干条（见
 * {@link HistoryWindow#fetchLimit()}），所以 {@code droppedMessages} 是本次实际没发出去的条数，
 * 不等于会话累计被裁掉的条数——后者由会话摘要的水位表达。</p>
 *
 * @param messages        实际发给模型的历史（时间正序）
 * @param droppedMessages 被裁掉的历史条数
 * @param droppedChars    被裁掉的历史正文总字符数
 * @param keptChars       保留的历史正文总字符数（不含图片字节）
 */
public record HistoryWindowResult(List<SessionMessage> messages, int droppedMessages, int droppedChars,
                                 int keptChars) {

    /**
     * 归一化：消息列表永远不可变、不可为 null。
     */
    public HistoryWindowResult {
        messages = List.copyOf(messages);
    }

    /**
     * 未发生裁剪的结果（窗口未启用，或本来就没超预算）。
     */
    public static HistoryWindowResult all(List<SessionMessage> history) {
        var all = List.copyOf(history);
        int chars = 0;
        for (SessionMessage message : all) {
            chars += HistoryWindow.charsOf(message);
        }
        return new HistoryWindowResult(all, 0, 0, chars);
    }

    /**
     * 是否发生了裁剪。
     */
    public boolean trimmed() {
        return droppedMessages > 0;
    }

    /**
     * 保留的历史条数。
     */
    public int keptMessages() {
        return messages.size();
    }
}
