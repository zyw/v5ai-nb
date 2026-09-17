package xin.v5ai.nb.common.agentscope.core.domain.dto;

import xin.v5ai.nb.common.agentscope.enums.McpToolCallStatus;
import xin.v5ai.nb.common.agentscope.enums.McpToolPermission;

import java.time.Instant;

/**
 * MCP Tool 调用审计记录：每次工具调用都会落库，用于追溯与合规。
 *
 * @param id               主键
 * @param runId            所属运行 ID
 * @param serverId         所属 MCP Server
 * @param serverName       MCP Server 名称（冗余存储，便于审计查询）
 * @param toolName         工具名
 * @param argumentsSummary 参数摘要（截断，不落完整敏感参数）
 * @param decision         平台权限决策（ALLOW / APPROVE / DENY）
 * @param status           调用结果状态
 * @param durationMs       调用耗时（毫秒）
 * @param message          错误信息（失败时）
 * @param createdAt        记录时间
 */
public record McpToolCallAuditDTO(
        Long id,
        String runId,
        Long serverId,
        String serverName,
        String toolName,
        String argumentsSummary,
        McpToolPermission decision,
        McpToolCallStatus status,
        Long durationMs,
        String message,
        Instant createdAt
) {
}
