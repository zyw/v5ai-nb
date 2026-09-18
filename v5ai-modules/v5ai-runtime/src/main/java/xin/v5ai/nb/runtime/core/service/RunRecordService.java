package xin.v5ai.nb.runtime.core.service;

import xin.v5ai.nb.common.agentscope.core.domain.bo.AgentRunBo;

import java.util.Optional;

/**
 * 运行记录仓储端口：管理一次 AgentDTO 运行的完整生命周期。
 * 实现通常对接数据库表（如 v5ai_run），供运行审计与状态查询使用。
 */
public interface RunRecordService {
    /**
     * 记录运行开始：写入一条运行记录。
     *
     * @param runId   本次运行的唯一 ID
     * @param request 触发本次运行的请求（用于记录入参）
     */
    boolean start(String runId, AgentRunBo request);

    /**
     * 标记运行成功完成。
     *
     * @param runId 运行 ID
     */
    boolean complete(String runId);

    /**
     * 标记运行失败并记录失败原因。
     *
     * @param runId   运行 ID
     * @param message 失败信息
     */
    boolean fail(String runId, String message);

    /**
     * 标记运行已取消（调用方主动停止）。
     *
     * <p>与失败区分开：停止是正常的收尾动作，不写错误信息。
     * 只在当前仍是 RUNNING 时生效，因此对已完成/已取消的运行是幂等的空操作。</p>
     *
     * @param runId 运行 ID
     * @return 是否真的把它从 RUNNING 改成了 CANCELED
     */
    boolean cancel(String runId);

    /**
     * 查一次运行所属的会话，供停止端点做归属校验。
     *
     * @param runId 运行 ID
     * @return 会话 ID；运行不存在时为空
     */
    Optional<String> findConversationId(String runId);
}
