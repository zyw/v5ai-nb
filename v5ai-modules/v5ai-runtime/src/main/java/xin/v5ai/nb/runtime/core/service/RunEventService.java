package xin.v5ai.nb.runtime.core.service;

import xin.v5ai.nb.common.agentscope.core.domain.dto.RuntimeRunEventDTO;

/**
 * 运行事件仓储端口：持久化每次运行的每一条事件。
 * 实现通常对接数据库表（如 v5ai_run_event），runId 关联到运行记录。
 */
public interface RunEventService {
    /**
     * 保存一条运行事件（开始/文本增量/模型调用/失败/完成等）。
     *
     * @param event 要保存的运行事件
     */
    boolean save(RuntimeRunEventDTO event);
}
