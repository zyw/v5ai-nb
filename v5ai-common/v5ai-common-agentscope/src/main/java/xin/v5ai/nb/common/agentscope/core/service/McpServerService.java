package xin.v5ai.nb.common.agentscope.core.service;

import xin.v5ai.nb.common.agentscope.core.domain.McpServerDTO;

public interface McpServerService {
    McpServerDTO selectById(Long id);
}
