package xin.v5ai.nb.runtime.core.service;

import xin.v5ai.nb.common.agentscope.core.domain.bo.AgentRunBo;

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
}
