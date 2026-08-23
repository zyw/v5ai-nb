package xin.v5ai.nb.common.agentscope.core.domain;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * <p>
 * Agent-MCP Server 绑定实体（v5ai_agent_mcp）：复合主键 (agent_key, mcp_server_id)。
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Getter
@Setter
@ToString
public class AgentMcpBindingDTO {

    private String agentKey;

    /**
     * 绑定的 MCP Server
     */
    private Long mcpServerId;

    private LocalDateTime createdAt;
}
