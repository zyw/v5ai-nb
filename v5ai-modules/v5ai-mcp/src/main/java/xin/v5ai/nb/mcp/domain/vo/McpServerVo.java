package xin.v5ai.nb.mcp.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.mcp.domain.McpServer;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * MCP Server 视图对象：headers/envVars 为解密后的明文（由服务填充）。
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Data
@AutoMapper(target = McpServer.class)
public class McpServerVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    /**
     * 传输类型（STREAMABLE_HTTP / SSE / STDIO）
     */
    private String transportType;

    private String endpoint;

    /**
     * Stdio 命令参数（由服务解析 argsJson 填充）
     */
    private List<String> args;

    /**
     * HTTP 请求头（由服务解密填充）
     */
    private Map<String, String> headers;

    /**
     * Stdio 环境变量（由服务解密填充）
     */
    private Map<String, String> envVars;

    private Integer timeoutSeconds;

    /**
     * 启用状态（ACTIVE / DISABLED）
     */
    private String status;

    private String lastTestStatus;

    private String lastTestMessage;

    private OffsetDateTime lastTestedAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
