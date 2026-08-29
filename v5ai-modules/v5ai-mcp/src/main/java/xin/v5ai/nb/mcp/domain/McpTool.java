package xin.v5ai.nb.mcp.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import xin.v5ai.nb.common.mybatis.core.domain.BaseEntity;

import java.time.OffsetDateTime;

/**
 * <p>
 * MCP Tool 实体（v5ai_mcp_tool）：缓存自 Tool 发现结果，permission 为平台权限决策。
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode(callSuper = true)
@TableName("v5ai_mcp_tool")
public class McpTool extends BaseEntity {

    @TableId(value = "id", type = IdType.AUTO)
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
