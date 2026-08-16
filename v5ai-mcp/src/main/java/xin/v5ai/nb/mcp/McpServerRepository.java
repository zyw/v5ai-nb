package xin.v5ai.nb.mcp;

import java.util.List;

public interface McpServerRepository {
    McpServer save(McpServer server);

    McpServer update(McpServer server);

    McpServer findById(Long id);

    List<McpServer> list();

    /** 禁用（逻辑删除）：绑定的 Agent 不再加载该 Server 的 Tool。 */
    void disable(Long id);
}
