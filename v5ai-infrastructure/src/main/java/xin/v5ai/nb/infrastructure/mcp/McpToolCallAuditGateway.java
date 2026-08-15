package xin.v5ai.nb.infrastructure.mcp;

import java.util.List;

public interface McpToolCallAuditGateway {
    void insertAudit(McpToolCallAuditEntity entity);

    List<McpToolCallAuditEntity> selectByRunId(String runId);

    List<McpToolCallAuditEntity> selectByServerId(Long serverId);
}
