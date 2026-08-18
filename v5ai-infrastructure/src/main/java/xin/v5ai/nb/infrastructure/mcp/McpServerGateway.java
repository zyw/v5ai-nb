package xin.v5ai.nb.infrastructure.mcp;

import java.util.List;

public interface McpServerGateway {
    void insertServer(McpServerEntity entity);

    void updateServer(McpServerEntity entity);

    McpServerEntity selectServerById(Long id);

    List<McpServerEntity> selectAll();

    /** 下拉选项投影：仅 id/name，不解密加载请求头/环境变量密文。 */
    List<McpServerEntity> selectServerOptions();

    void disableServer(Long id);
}
