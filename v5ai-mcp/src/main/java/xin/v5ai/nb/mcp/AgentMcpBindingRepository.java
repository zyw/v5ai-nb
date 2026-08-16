package xin.v5ai.nb.mcp;

import java.util.List;

public interface AgentMcpBindingRepository {
    /** 全量替换某 Agent 绑定的 MCP Server 集合。 */
    void save(String agentKey, List<Long> mcpServerIds);

    List<Long> findByAgentKey(String agentKey);

    void delete(String agentKey);
}
