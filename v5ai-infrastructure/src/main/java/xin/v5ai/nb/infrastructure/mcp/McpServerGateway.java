package xin.v5ai.nb.infrastructure.mcp;

import java.util.List;

public interface McpServerGateway {
    void insertServer(McpServerEntity entity);

    void updateServer(McpServerEntity entity);

    McpServerEntity selectServerById(Long id);

    List<McpServerEntity> selectAll();

    void disableServer(Long id);
}
