package xin.v5ai.nb.mcp.core;

import xin.v5ai.nb.mcp.core.domain.dto.AgentMcpBindingDTO;

import java.util.List;

/**
 * 绑定服务
 */
public interface AgentMcpBindingService {
    /**
     * 删除
     */
    void delete(String agentKey);

    /**
     * 查询列表
     */
    List<AgentMcpBindingDTO> selectList(String agentKey);
}
