package xin.v5ai.nb.infrastructure.mcp;

import org.springframework.stereotype.Repository;
import xin.v5ai.nb.mcp.McpToolCallAudit;
import xin.v5ai.nb.mcp.McpToolCallAuditRepository;
import xin.v5ai.nb.mcp.McpToolCallStatus;
import xin.v5ai.nb.mcp.McpToolPermission;

import java.util.List;

@Repository
public class MyBatisMcpToolCallAuditRepository implements McpToolCallAuditRepository {
    private final McpToolCallAuditGateway gateway;

    public MyBatisMcpToolCallAuditRepository(McpToolCallAuditGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public McpToolCallAudit save(McpToolCallAudit audit) {
        var entity = new McpToolCallAuditEntity();
        entity.setRunId(audit.runId());
        entity.setServerId(audit.serverId());
        entity.setServerName(audit.serverName());
        entity.setToolName(audit.toolName());
        entity.setArgumentsSummary(audit.argumentsSummary());
        entity.setDecision(audit.decision().name());
        entity.setStatus(audit.status().name());
        entity.setDurationMs(audit.durationMs());
        entity.setMessage(audit.message());
        entity.setCreatedAt(audit.createdAt() == null ? java.time.Instant.now() : audit.createdAt());
        gateway.insertAudit(entity);
        return new McpToolCallAudit(entity.getId(), entity.getRunId(), entity.getServerId(),
                entity.getServerName(), entity.getToolName(), entity.getArgumentsSummary(),
                McpToolPermission.valueOf(entity.getDecision()), McpToolCallStatus.valueOf(entity.getStatus()),
                entity.getDurationMs(), entity.getMessage(), entity.getCreatedAt());
    }

    @Override
    public List<McpToolCallAudit> findByRunId(String runId) {
        return gateway.selectByRunId(runId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<McpToolCallAudit> findByServerId(Long serverId) {
        return gateway.selectByServerId(serverId).stream().map(this::toDomain).toList();
    }

    private McpToolCallAudit toDomain(McpToolCallAuditEntity entity) {
        return new McpToolCallAudit(entity.getId(), entity.getRunId(), entity.getServerId(),
                entity.getServerName(), entity.getToolName(), entity.getArgumentsSummary(),
                McpToolPermission.valueOf(entity.getDecision()), McpToolCallStatus.valueOf(entity.getStatus()),
                entity.getDurationMs(), entity.getMessage(), entity.getCreatedAt());
    }
}
