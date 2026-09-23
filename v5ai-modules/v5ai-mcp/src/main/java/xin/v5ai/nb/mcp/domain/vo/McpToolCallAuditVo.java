package xin.v5ai.nb.mcp.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.mcp.domain.McpToolCallAudit;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * <p>
 * MCP Tool 调用审计视图对象
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Data
@AutoMapper(target = McpToolCallAudit.class)
public class McpToolCallAuditVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String runId;

    private Long serverId;

    private String serverName;

    private String toolName;

    private String argumentsSummary;

    /**
     * 平台权限决策（ALLOW / APPROVE / DENY）
     */
    private String decision;

    /**
     * 调用结果状态
     */
    private String status;

    private Long durationMs;

    private String message;

    private Instant createdAt;
}
