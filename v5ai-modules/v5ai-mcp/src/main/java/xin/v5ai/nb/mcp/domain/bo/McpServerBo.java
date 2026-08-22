package xin.v5ai.nb.mcp.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import xin.v5ai.nb.common.agentscope.enums.McpTransportType;
import xin.v5ai.nb.common.core.validate.EditGroup;
import xin.v5ai.nb.mcp.domain.McpServer;

import java.util.List;
import java.util.Map;

/**
 * MCP Server 请求体（创建/更新/查询）。
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Data
@AutoMapper(target = McpServer.class, reverseConvertGenerate = false)
public class McpServerBo {

    /**
     * 主键，编辑时必填
     */
    @NotNull(message = "主键不能为空", groups = {EditGroup.class})
    private Long id;

    /**
     * 平台内唯一标识名
     */
    @NotBlank(message = "MCP server name is required")
    private String name;

    /**
     * 传输类型（STREAMABLE_HTTP / SSE / STDIO）
     */
    @NotNull(message = "transport type is required")
    private McpTransportType transportType;

    /**
     * HTTP 传输为 URL；Stdio 传输为可执行命令
     */
    private String endpoint;

    /**
     * Stdio 命令参数（明文）
     */
    private List<String> args;

    /**
     * HTTP 请求头（明文，持久化层负责加密）
     */
    private Map<String, String> headers;

    /**
     * Stdio 环境变量（明文，持久化层负责加密）
     */
    private Map<String, String> envVars;

    /**
     * 请求超时秒数，默认 30
     */
    private Integer timeoutSeconds;

    /**
     * 启用状态（查询条件：ACTIVE / DISABLED）
     */
    private String status;
}
