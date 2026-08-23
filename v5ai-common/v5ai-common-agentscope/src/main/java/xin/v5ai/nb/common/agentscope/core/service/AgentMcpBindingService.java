package xin.v5ai.nb.common.agentscope.core.service;

import xin.v5ai.nb.common.agentscope.core.domain.AgentMcpBindingDTO;

import java.util.List;

public interface AgentMcpBindingService {
    void delete(String agentKey);

    List<AgentMcpBindingDTO> selectList(String agentKey);
}
