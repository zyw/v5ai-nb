package xin.v5ai.nb.mcp.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.mcp.domain.McpTool;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * <p>
 * MCP Tool 视图对象：inputSchema 为解析后的 JSON Schema（由服务填充）。
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Data
@AutoMapper(target = McpTool.class)
public class McpToolVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long serverId;

    private String toolName;

    private String description;

    /**
     * 输入 JSON Schema（由服务解析 inputSchemaJson 填充）
     */
    private Map<String, Object> inputSchema;

    private Boolean readOnly;

    /**
     * 平台权限决策（ALLOW / APPROVE / DENY）
     */
    private String permission;

    private OffsetDateTime lastDiscoveredAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
