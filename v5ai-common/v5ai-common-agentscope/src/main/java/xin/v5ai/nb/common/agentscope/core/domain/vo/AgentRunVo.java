package xin.v5ai.nb.common.agentscope.core.domain.vo;

/**
 * AgentDTO 运行结果（不可变 record）。
 *
 * @param runId   本次运行的唯一标识（用于追踪日志、关联会话等）
 * @param answer  AgentDTO 最终生成的回答内容
 */
public record AgentRunVo(String runId, String answer) {
}
