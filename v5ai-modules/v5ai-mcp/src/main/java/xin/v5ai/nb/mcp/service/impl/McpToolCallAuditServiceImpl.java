package xin.v5ai.nb.mcp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.common.agentscope.core.domain.dto.McpToolCallAuditDTO;
import xin.v5ai.nb.common.agentscope.core.service.McpToolCallAuditService;
import xin.v5ai.nb.common.agentscope.enums.McpToolCallStatus;
import xin.v5ai.nb.common.agentscope.enums.McpToolPermission;
import xin.v5ai.nb.mcp.domain.McpToolCallAudit;
import xin.v5ai.nb.mcp.mapper.McpToolCallAuditMapper;
import xin.v5ai.nb.mcp.service.IMcpToolCallAuditService;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class McpToolCallAuditServiceImpl implements IMcpToolCallAuditService, McpToolCallAuditService {

    private final McpToolCallAuditMapper baseMapper;

    @Override
    public McpToolCallAuditDTO save(McpToolCallAuditDTO audit) {
        var entity = new McpToolCallAudit();
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
        baseMapper.insert(entity);
        return toDomain(entity);
    }

    @Override
    public List<McpToolCallAuditDTO> findByRunId(String runId) {
        return baseMapper.selectList(new LambdaQueryWrapper<McpToolCallAudit>()
                        .eq(xin.v5ai.nb.mcp.domain.McpToolCallAudit::getRunId, runId)
                        .orderByDesc(xin.v5ai.nb.mcp.domain.McpToolCallAudit::getId))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<McpToolCallAuditDTO> findByServerId(Long serverId) {
        return baseMapper.selectList(new LambdaQueryWrapper<McpToolCallAudit>()
                        .eq(xin.v5ai.nb.mcp.domain.McpToolCallAudit::getServerId, serverId)
                        .orderByDesc(xin.v5ai.nb.mcp.domain.McpToolCallAudit::getId))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private McpToolCallAuditDTO toDomain(xin.v5ai.nb.mcp.domain.McpToolCallAudit entity) {
        return new McpToolCallAuditDTO(
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
