package xin.v5ai.nb.mcp.core.service;

import xin.v5ai.nb.mcp.core.domain.dto.McpServerDTO;
import xin.v5ai.nb.mcp.domain.McpServer;

public interface McpServerService {
    /**
     * 根据 ID 查询 MCP 服务器信息。
     *
     * @param id 服务器 ID
     * @return MCP 服务器信息
     */
    McpServerDTO selectById(Long id);
    /**
     * 将 MCP 服务器实体转换为 DTO。
     *
     * @param entity MCP 服务器实体
     * @return MCP 服务器 DTO
     */
    default McpServerDTO buildMcpServerDTO(McpServer entity) {
        var dto = new McpServerDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setTransportType(entity.getTransportType());
        dto.setEndpoint(entity.getEndpoint());
        dto.setArgsJson(entity.getArgsJson());
        dto.setHeadersCiphertext(entity.getHeadersCiphertext());
        dto.setEnvCiphertext(entity.getEnvCiphertext());
        dto.setTimeoutSeconds(entity.getTimeoutSeconds());
        dto.setStatus(entity.getStatus());
        dto.setLastTestStatus(entity.getLastTestStatus());
        dto.setLastTestMessage(entity.getLastTestMessage());
        dto.setLastTestedAt(entity.getLastTestedAt());
        return dto;
    }
}
