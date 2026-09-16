package xin.v5ai.nb.agent.core;

import java.util.List;

/**
 * 模型生成的 AgentDTO 配置（用于「新建智能体」向导的生成阶段回填）。
 *
 * @param name            生成的智能体名称
 * @param description     生成的描述
 * @param greeting        生成的欢迎语
 * @param presetQuestions 生成的预设问题列表
 * @param systemPrompt    生成的系统提示词
 */
public record GeneratedAgentConfig(
        String name,
        String description,
        String greeting,
        List<String> presetQuestions,
        String systemPrompt
) {
}
