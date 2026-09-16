package xin.v5ai.nb.common.agentscope.enums;

/**
 * RAG 调用方式：
 * <ul>
 *   <li>{@link #SMART}（智能调用）：注册 {@code rag_search} 工具，由 LLM 自主判断是否检索、检索哪个知识库、调用几次；</li>
 *   <li>{@link #FORCED}（强制调用）：每次运行都在模型前检索并注入系统提示（历史行为）。</li>
 * </ul>
 * 仅在 AgentDTO 的 {@code ragEnabled} 开启时生效。
 */
public enum RagCallMode {
    /**
     * 智能调用。
     */
    SMART(1),
    /**
     * 强制调用。
     */
    FORCED(2);

    private final int value;

    RagCallMode(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    /** 从 DB 数值解析；未知值一律回退 {@link #FORCED}（与迁移默认 2 一致，保证存量行为不变）。 */
    public static RagCallMode from(int value) {
        return value == SMART.value ? SMART : FORCED;
    }
}