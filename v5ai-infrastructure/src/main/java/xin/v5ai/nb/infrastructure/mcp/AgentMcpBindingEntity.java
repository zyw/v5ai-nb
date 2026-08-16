package xin.v5ai.nb.infrastructure.mcp;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("v5ai_agent_mcp")
public class AgentMcpBindingEntity {
    @TableId(value = "agent_key")
    private String agentKey;
    @TableField("mcp_server_id")
    private Long mcpServerId;

    public String getAgentKey() { return agentKey; }
    public void setAgentKey(String agentKey) { this.agentKey = agentKey; }
    public Long getMcpServerId() { return mcpServerId; }
    public void setMcpServerId(Long mcpServerId) { this.mcpServerId = mcpServerId; }
}
