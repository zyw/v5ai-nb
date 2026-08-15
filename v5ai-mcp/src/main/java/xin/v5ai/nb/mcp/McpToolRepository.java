package xin.v5ai.nb.mcp;

import java.util.List;

public interface McpToolRepository {
    /** 按 (serverId, toolName) 幂等保存：已存在则更新元数据并保留权限设置。 */
    McpToolDefinition upsert(McpToolDefinition tool);

    List<McpToolDefinition> findByServerId(Long serverId);

    McpToolDefinition findByServerAndName(Long serverId, String toolName);

    void updatePermission(Long serverId, String toolName, McpToolPermission permission);

    void deleteByServerId(Long serverId);

    /** 删除该 Server 下不在 keepToolNames 中的 Tool（发现刷新时清理已下线的工具）。 */
    void deleteByServerIdNotIn(Long serverId, List<String> keepToolNames);
}
