package xin.v5ai.nb.agent.core;

/**
 * 端口：根据用户描述调用指定模型生成 AgentDTO 配置
 * （名称、描述、欢迎语、预设问题、系统提示词）。
 */
public interface AgentConfigGenerator {
    /**
     * 调用模型生成 AgentDTO 配置。
     *
     * @param modelId     用于生成的模型 ID（须已配置且可用）
     * @param description 用户对目标智能体的描述
     * @return 生成的配置（字段可能部分为空，由实现决定）
     * @throws IllegalArgumentException 模型未配置 / 调用失败 / 输出无法解析时抛出
     */
    GeneratedAgentConfig generate(Long modelId, String description);
}
