package xin.v5ai.nb.mcp.core.domain.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Getter
@Setter
@ToString
public class McpToolDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /**
     * 所属 MCP Server
     */
    private Long serverId;

    /**
     * 工具名（MCP 协议内唯一，与 serverId 组成唯一键）
     */
    private String toolName;

    /**
     * 工具描述
     */
    private String description;

    /**
     * 输入 JSON Schema（JSON 文本）
     */
    private String inputSchemaJson;

    /**
     * 服务器声明的只读提示
     */
    private Boolean readOnly;

    /**
     * 平台权限决策（ALLOW / APPROVE / DENY）
     */
    private String permission;

    /**
     * 最近一次发现时间
     */
    private OffsetDateTime lastDiscoveredAt;
}
