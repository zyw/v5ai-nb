package xin.v5ai.nb.mcp;

import java.util.List;

public interface McpServerRepository {
    McpServer save(McpServer server);

    McpServer update(McpServer server);

    McpServer findById(Long id);

    List<McpServer> list();

    /**
     * MCP Server 下拉选项轻量视图。默认基于全量列表推导，持久化实现可覆盖为投影查询以降低大表开销。
     */
    default List<McpServerOption> listOptions() {
        return list().stream()
                .map(server -> new McpServerOption(server.id(), server.name()))
                .toList();
    }

    /** 禁用（逻辑删除）：绑定的 Agent 不再加载该 Server 的 Tool。 */
    void disable(Long id);
}
