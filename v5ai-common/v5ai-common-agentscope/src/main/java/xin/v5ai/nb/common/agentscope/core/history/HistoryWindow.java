package xin.v5ai.nb.common.agentscope.core.history;

import xin.v5ai.nb.common.agentscope.core.domain.SessionMessage;
import xin.v5ai.nb.common.agentscope.enums.MessageRole;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 同会话历史的窗口裁剪（无状态纯函数）：从最新一条往前保留，直到触到条数或字符预算。
 *
 * <p>三条刻意的规则：</p>
 * <ul>
 *   <li><b>至少一条</b>：最新那条无论多长都要给模型——空上下文比超预算更糟；</li>
 *   <li><b>保底条数</b>：{@code minMessages} 之内不看预算，避免「刚问完就被裁掉」的割裂感；</li>
 *   <li><b>边界对齐</b>：裁剪后首条不能是助手回答（没有对应提问的回答会让模型串味），
 *       必要时继续往前丢。</li>
 * </ul>
 *
 * <p>字符预算只算正文，图片由 {@code historyImageBudgetBytes} 的字节预算单独约束。</p>
 */
public final class HistoryWindow {

    /** SQL 侧多取几倍的余量：边界对齐还要再丢几条，取刚刚好会把窗口喂不满。 */
    private static final int FETCH_FACTOR = 2;

    /** SQL 侧取数的下限：条数预算很小时也要够对齐用。 */
    private static final int MIN_FETCH_LIMIT = 20;

    /** 不裁剪的窗口：预算无限、不保底、不对齐。 */
    private static final HistoryWindow UNLIMITED =
            new HistoryWindow(Integer.MAX_VALUE, Integer.MAX_VALUE, 0, false);

    private final int maxMessages;
    private final int maxChars;
    private final int minMessages;
    private final boolean alignToUserTurn;

    /**
     * @param maxMessages     保留条数上限；{@code <= 0} 表示不限
     * @param maxChars        保留正文字符上限；{@code <= 0} 表示不限
     * @param minMessages     保底条数（不受预算约束）
     * @param alignToUserTurn 是否把首条对齐到用户提问
     */
    public HistoryWindow(int maxMessages, int maxChars, int minMessages, boolean alignToUserTurn) {
        this.maxMessages = maxMessages <= 0 ? Integer.MAX_VALUE : maxMessages;
        this.maxChars = maxChars <= 0 ? Integer.MAX_VALUE : maxChars;
        this.minMessages = Math.max(minMessages, 0);
        this.alignToUserTurn = alignToUserTurn;
    }

    /**
     * 不裁剪的窗口（窗口开关关闭时使用）。
     */
    public static HistoryWindow unlimited() {
        return UNLIMITED;
    }

    /**
     * SQL 侧一次取多少条历史。
     *
     * @return 建议的取数条数；{@code -1} 表示不限（调用方可以退回全量查询）
     */
    public int fetchLimit() {
        if (maxMessages == Integer.MAX_VALUE) {
            return -1;
        }
        return Math.max(maxMessages * FETCH_FACTOR, MIN_FETCH_LIMIT);
    }

    /**
     * 按预算裁剪历史。
     *
     * @param history 该会话的历史（时间正序；通常已不含本轮提问）
     * @return 裁剪结果（含统计）；{@code history} 为空时返回空结果
     */
    public HistoryWindowResult select(List<SessionMessage> history) {
        if (history == null || history.isEmpty()) {
            return HistoryWindowResult.all(List.of());
        }
        int totalChars = 0;
        for (SessionMessage message : history) {
            totalChars += charsOf(message);
        }
        if (history.size() <= maxMessages && totalChars <= maxChars) {
            return HistoryWindowResult.all(history);
        }

        // 由新到旧累积：至少保留最新的一条（下面 !kept.isEmpty() 的短路）
        var kept = new ArrayList<SessionMessage>();
        int keptChars = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            var message = history.get(i);
            int chars = charsOf(message);
            if (!kept.isEmpty() && (kept.size() >= maxMessages || keptChars + chars > maxChars)) {
                break;
            }
            kept.add(message);
            keptChars += chars;
        }
        // 保底条数：仍然由新到旧补，补完再反转
        for (int i = history.size() - kept.size() - 1; i >= 0 && kept.size() < minMessages; i--) {
            var message = history.get(i);
            kept.add(message);
            keptChars += charsOf(message);
        }
        Collections.reverse(kept);

        if (alignToUserTurn) {
            int from = 0;
            while (from < kept.size() && kept.get(from).role() != MessageRole.USER) {
                from++;
            }
            // 全是被裁出来的助手回答（极端情况）时保持原样：有总比没有强
            if (from > 0 && from < kept.size()) {
                keptChars = 0;
                var aligned = new ArrayList<SessionMessage>(kept.size() - from);
                for (int i = from; i < kept.size(); i++) {
                    aligned.add(kept.get(i));
                    keptChars += charsOf(kept.get(i));
                }
                kept = aligned;
            }
        }

        return new HistoryWindowResult(kept, history.size() - kept.size(), totalChars - keptChars, keptChars);
    }

    /**
     * 单条消息计入字符预算的长度：正文长度；{@code null} 正文按 0 计。
     */
    static int charsOf(SessionMessage message) {
        return message.content() == null ? 0 : message.content().length();
    }
}
