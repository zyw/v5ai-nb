package xin.v5ai.nb.common.agentscope.core.domain;

import cn.hutool.json.JSONUtil;

/**
 * 一次运行的用量汇总（不可变 record）：token 估算 + 服务端耗时。
 *
 * <p>同一个对象有三个出口，且必须是同一份数字：</p>
 * <ul>
 *   <li>{@code RUN_COMPLETED} 的事件载荷（{@link #toJson()}，前端边收边展示）；</li>
 *   <li>助手消息落库（{@code v5ai_message.prompt_tokens/completion_tokens/duration_ms}，
 *       刷新页面后读历史仍能看到）；</li>
 *   <li>模型用量明细与配额记账（{@code v5ai_model_usage}）。</li>
 * </ul>
 *
 * <p>token 是平台**按字符数估算**的值（输入 = 提问 + RAG 上下文，输出 = 回答，每 4 字符约 1 token），
 * 不是模型回报的真实用量。</p>
 *
 * @param promptTokens     输入 token 估算
 * @param completionTokens 输出 token 估算
 * @param totalTokens      总 token（恒等于前两项之和，构造时归一化，避免两处各算一遍）
 * @param durationMs       服务端从 RUN_STARTED 到收尾的墙钟耗时（毫秒）
 */
public record RunUsage(long promptTokens, long completionTokens, long totalTokens, long durationMs) {

    /**
     * 归一化：总数永远由两项相加得出，调用方不必自己算、也不会传错。
     */
    public RunUsage {
        totalTokens = promptTokens + completionTokens;
    }

    /**
     * 按两项 token 构造（自动求和）。
     */
    public static RunUsage of(long promptTokens, long completionTokens, long durationMs) {
        return new RunUsage(promptTokens, completionTokens, promptTokens + completionTokens, durationMs);
    }

    /**
     * 事件载荷（字段名即前端读的名字，与历史接口返回的形状完全一致）。
     */
    public String toJson() {
        return JSONUtil.toJsonStr(this);
    }

    /**
     * 三列都为空时视为「没有用量」（用户消息、V39 之前的历史回答）。
     *
     * @param promptTokens     可空的输入 token
     * @param completionTokens 可空的输出 token
     * @param durationMs       可空的耗时
     * @return 三者齐备时返回用量，否则返回 {@code null}
     */
    public static RunUsage fromNullable(Integer promptTokens, Integer completionTokens, Integer durationMs) {
        if (promptTokens == null || completionTokens == null || durationMs == null) {
            return null;
        }
        return RunUsage.of(promptTokens, completionTokens, durationMs);
    }

    /**
     * 落库用的耗时（毫秒）转 int：极端时钟跳变下宁可截断到上限，也不要溢出成负数。
     */
    public int durationMsAsInt() {
        return (int) Math.min(Math.max(durationMs, 0), Integer.MAX_VALUE);
    }
}
