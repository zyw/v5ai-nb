package xin.v5ai.nb.agent.controller.vo;

import xin.v5ai.nb.agent.core.GeneratedAgentConfig;

import java.util.List;

/**
 * 新建智能体向导的配置生成响应体。
 *
 * @author ZYW
 * @since 2026-08-22
 */
public record GenerateAgentConfigResponse(
        String name,
        String description,
        String greeting,
        List<String> presetQuestions,
        String systemPrompt
) {
    public static GenerateAgentConfigResponse from(GeneratedAgentConfig config) {
        return new GenerateAgentConfigResponse(config.name(), config.description(), config.greeting(),
                config.presetQuestions() == null ? List.of() : config.presetQuestions(),
                config.systemPrompt());
    }
}
