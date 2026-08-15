package xin.v5ai.nb.infrastructure.mcp;

import java.util.List;

public interface McpToolGateway {
    void insertTool(McpToolEntity entity);

    void updateTool(McpToolEntity entity);

    McpToolEntity selectToolById(Long id);

    McpToolEntity selectToolByServerAndName(Long serverId, String toolName);

    List<McpToolEntity> selectToolsByServer(Long serverId);

    void deleteToolsByServer(Long serverId);

    void deleteToolsByServerNotIn(Long serverId, List<String> keepToolNames);

    void updateToolPermission(Long serverId, String toolName, String permission);
}
