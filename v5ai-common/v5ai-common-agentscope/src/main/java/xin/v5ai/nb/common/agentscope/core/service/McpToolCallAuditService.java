package xin.v5ai.nb.common.agentscope.core.service;

import xin.v5ai.nb.common.agentscope.core.domain.McpToolCallAudit;

import java.util.List;

public interface McpToolCallAuditService {
    McpToolCallAudit save(McpToolCallAudit audit);

    List<McpToolCallAudit> findByRunId(String runId);

    List<McpToolCallAudit> findByServerId(Long serverId);
}
