package xin.v5ai.nb.mcp.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import xin.v5ai.nb.mcp.domain.AgentMcpBinding;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * <p>
 * AgentDTO-MCP Server 绑定实体（v5ai_agent_mcp）：复合主键 (agent_key, mcp_server_id)。
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Getter
@Setter
@ToString
@AutoMapper(target= AgentMcpBinding.class)
public class AgentMcpBindingBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String agentKey;

    /**
     * 绑定的 MCP Server
     */
    private Long mcpServerId;

    private OffsetDateTime createdAt;
}
