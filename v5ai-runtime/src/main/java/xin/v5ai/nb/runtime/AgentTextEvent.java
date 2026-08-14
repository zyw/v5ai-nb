package xin.v5ai.nb.runtime;

/**
 * 由 {@link AgentTextExecutor} 产生的事件（sealed，仅允许两种实现）。
 *
 * - Text：文本增量片段，直接流式推送给客户端；
 * - ModelCall：模型调用标记，被上层转换为平台统一的 {@code MODEL_CALL} 事件。
 */
public sealed interface AgentTextEvent permits AgentTextEvent.Text, AgentTextEvent.ModelCall {

    /**
     * 文本增量：Agent 输出的一段文字内容。
     *
     * @param text 文本片段
     */
    record Text(String text) implements AgentTextEvent {
    }

    /**
     * 模型调用标记：标识一次模型调用开始（携带模型名）。
     *
     * @param modelName 本次调用使用的模型名称
     */
    record ModelCall(String modelName) implements AgentTextEvent {
    }
}
