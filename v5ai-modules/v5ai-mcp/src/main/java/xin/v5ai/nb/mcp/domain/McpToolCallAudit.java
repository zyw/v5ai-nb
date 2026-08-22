package xin.v5ai.nb.mcp.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

/**
 * <p>
 * MCP Tool 调用审计实体（v5ai_mcp_tool_call）：每次工具调用都会落库，用于追溯与合规。
 * 表无 updated_at 列，故不继承 BaseEntity。
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Getter
@Setter
@ToString
@TableName("v5ai_mcp_tool_call")
public class McpToolCallAudit {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属运行 ID
     */
    private String runId;

    /**
     * 所属 MCP Server
     */
    private Long serverId;

    /**
     * MCP Server 名称（冗余存储，便于审计查询）
     */
    private String serverName;

    /**
     * 工具名
     */
    private String toolName;

    /**
     * 参数摘要（截断，不落完整敏感参数）
     */
    private String argumentsSummary;

    /**
     * 平台权限决策（ALLOW / APPROVE / DENY）
     */
    private String decision;

    /**
     * 调用结果状态
     */
    private String status;

    /**
     * 调用耗时（毫秒）
     */
    private Long durationMs;

    /**
     * 错误信息（失败时）
     */
    private String message;

    /**
     * 记录时间
     */
    private Instant createdAt;
}
