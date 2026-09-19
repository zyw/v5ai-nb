package xin.v5ai.nb.common.agentscope.core.token;

import io.agentscope.core.model.ChatUsage;
import xin.v5ai.nb.common.agentscope.core.AgentTextEvent;

/**
 * 一次运行的模型用量累加器：把模型回报的真实用量攒起来，供执行器在流末尾发出
 * {@link AgentTextEvent.Usage}（{@code estimated=false}）事件。
 *
 * <p>两种消费方式对应两条执行路径，语义不同、不能混用：</p>
 * <ul>
 *   <li>{@link #addCall(ChatUsage)}：HarnessAgent 路径。一次运行可能有多轮模型调用
 *       （工具循环），每轮一条 {@code ModelCallEndEvent}，**累加**；</li>
 *   <li>{@link #setCall(ChatUsage)}：直连路径。整个流就是一次调用，用量通常只出现在最后一个
 *       响应块上；即使服务端每块都带（累计值），取最后一次也仍然正确。</li>
 * </ul>
 *
 * <p>线程安全：每次订阅独占一个实例（在流构建方法里创建），因此不做同步；Reactor 保证同一订阅内
 * 的 onNext 串行。</p>
 */
public final class TokenUsageAccumulator {

    private long inputTokens;
    private long outputTokens;
    private boolean reported;

    /**
     * 累加一次模型调用的真实用量（多轮调用场景）。
     *
     * @param usage 模型回报的用量；为 {@code null}（服务端未回报）时忽略
     */
    public void addCall(ChatUsage usage) {
        if (usage == null) {
            return;
        }
        inputTokens += usage.getInputTokens();
        outputTokens += usage.getOutputTokens();
        reported = true;
    }

    /**
     * 覆盖式记录一次调用的真实用量（单次调用、可能分块重复回报的场景）。
     *
     * @param usage 模型回报的用量；为 {@code null} 时忽略
     */
    public void setCall(ChatUsage usage) {
        if (usage == null) {
            return;
        }
        inputTokens = usage.getInputTokens();
        outputTokens = usage.getOutputTokens();
        reported = true;
    }

    /**
     * 是否拿到过模型回报的真实用量。
     */
    public boolean reported() {
        return reported;
    }

    /**
     * 转成内部用量事件。
     *
     * @return 真实用量事件；一次都没拿到（服务端未回报）时返回 {@code null}
     */
    public AgentTextEvent.Usage toEvent() {
        return reported ? new AgentTextEvent.Usage(inputTokens, outputTokens, false) : null;
    }
}
