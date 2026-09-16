package xin.v5ai.nb.runtime.core.gateway;

import xin.v5ai.nb.runtime.core.domain.RuntimeRunEntity;

/**
 * 运行记录持久化网关：管理一次 AgentDTO 运行的完整生命周期。
 * 由基础设施层实现（MyBatis），对接 {@code v5ai_run} 表。
 */
public interface RuntimeRunGateway {
    /**
     * 插入一条运行记录（初始状态 RUNNING）。
     *
     * @param entity 运行实体（runId/conversationId/appKey/status）
     */
    void insertRun(RuntimeRunEntity entity);

    /**
     * 将运行标记为成功完成（COMPLETED）。
     *
     * @param runId 运行 ID
     */
    void markCompleted(String runId);

    /**
     * 将运行标记为失败（FAILED），并记录错误信息。
     *
     * @param runId        运行 ID
     * @param errorMessage 失败原因
     */
    void markFailed(String runId, String errorMessage);
}
