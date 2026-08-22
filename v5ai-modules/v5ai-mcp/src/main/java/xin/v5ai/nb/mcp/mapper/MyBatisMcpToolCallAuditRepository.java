package xin.v5ai.nb.mcp.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;
import xin.v5ai.nb.common.agentscope.core.domain.McpToolCallAudit;
import xin.v5ai.nb.common.agentscope.core.service.McpToolCallAuditService;
import xin.v5ai.nb.common.agentscope.enums.McpToolCallStatus;
import xin.v5ai.nb.common.agentscope.enums.McpToolPermission;

import java.time.Instant;
import java.util.List;

/**
 * {@link McpToolCallAuditService} 的 MyBatis-Plus 实现（运行时审计写入与查询）。
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Repository
public class MyBatisMcpToolCallAuditRepository implements McpToolCallAuditService {

    private final McpToolCallAuditMapper auditMapper;

    public MyBatisMcpToolCallAuditRepository(McpToolCallAuditMapper auditMapper) {
        this.auditMapper = auditMapper;
    }

    @Override
    public McpToolCallAudit save(McpToolCallAudit audit) {
        var entity = new xin.v5ai.nb.mcp.domain.McpToolCallAudit();
        entity.setRunId(audit.runId());
        entity.setServerId(audit.serverId());
        entity.setServerName(audit.serverName());
        entity.setToolName(audit.toolName());
        entity.setArgumentsSummary(audit.argumentsSummary());
        entity.setDecision(audit.decision().name());
        entity.setStatus(audit.status().name());
        entity.setDurationMs(audit.durationMs());
        entity.setMessage(audit.message());
        entity.setCreatedAt(audit.createdAt() == null ? Instant.now() : audit.createdAt());
        auditMapper.insert(entity);
        return toDomain(entity);
    }

    @Override
    public List<McpToolCallAudit> findByRunId(String runId) {
        return auditMapper.selectList(new LambdaQueryWrapper<xin.v5ai.nb.mcp.domain.McpToolCallAudit>()
                        .eq(xin.v5ai.nb.mcp.domain.McpToolCallAudit::getRunId, runId)
                        .orderByDesc(xin.v5ai.nb.mcp.domain.McpToolCallAudit::getId))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<McpToolCallAudit> findByServerId(Long serverId) {
        return auditMapper.selectList(new LambdaQueryWrapper<xin.v5ai.nb.mcp.domain.McpToolCallAudit>()
                        .eq(xin.v5ai.nb.mcp.domain.McpToolCallAudit::getServerId, serverId)
                        .orderByDesc(xin.v5ai.nb.mcp.domain.McpToolCallAudit::getId))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private McpToolCallAudit toDomain(xin.v5ai.nb.mcp.domain.McpToolCallAudit entity) {
        return new McpToolCallAudit(
                entity.getId(),
                entity.getRunId(),
                entity.getServerId(),
                entity.getServerName(),
                entity.getToolName(),
                entity.getArgumentsSummary(),
                McpToolPermission.valueOf(entity.getDecision()),
                McpToolCallStatus.valueOf(entity.getStatus()),
                entity.getDurationMs(),
                entity.getMessage(),
                entity.getCreatedAt());
    }
}
