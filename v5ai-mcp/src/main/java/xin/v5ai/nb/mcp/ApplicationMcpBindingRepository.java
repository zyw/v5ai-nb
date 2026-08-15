package xin.v5ai.nb.mcp;

import java.util.List;

public interface ApplicationMcpBindingRepository {
    /** 全量替换某 Application 绑定的 MCP Server 集合。 */
    void save(String appKey, List<Long> mcpServerIds);

    List<Long> findByAppKey(String appKey);

    void delete(String appKey);
}
