package xin.v5ai.nb.agent.controller.vo;

/**
 * 新建智能体向导的配置生成请求体。
 *
 * @param modelId     用于生成的模型 ID（须已配置且可用）
 * @param description 用户对目标智能体的描述
 * @author ZYW
 * @since 2026-08-22
 */
public record GenerateAgentConfigRequest(Long modelId, String description) {
}
