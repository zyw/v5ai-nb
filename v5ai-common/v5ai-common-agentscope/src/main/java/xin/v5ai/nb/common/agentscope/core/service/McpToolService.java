package xin.v5ai.nb.common.agentscope.core.service;

import xin.v5ai.nb.common.agentscope.core.domain.McpToolDTO;

import java.util.List;

public interface McpToolService {
    List<McpToolDTO> selectList(Long serverId);
}
