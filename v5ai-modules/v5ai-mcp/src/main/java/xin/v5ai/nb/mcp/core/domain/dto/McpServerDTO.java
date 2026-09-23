package xin.v5ai.nb.mcp.core.domain.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * <p>
 * MCP Server 实体（v5ai_mcp_server）：headers/env 属敏感配置，以应用级加密密钥加密后落库（密文列）。
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Getter
@Setter
@ToString
public class McpServerDTO implements Serializable {

    private Long id;

    /**
     * 平台内唯一标识名（也是 MCP 客户端名）
     */
    private String name;

    /**
     * 传输类型（STREAMABLE_HTTP / SSE / STDIO）
     */
    private String transportType;

    /**
     * HTTP 传输为 URL；Stdio 传输为可执行命令
     */
    private String endpoint;

    /**
     * Stdio 命令参数（JSON 文本）
     */
    private String argsJson;

    /**
     * HTTP 请求头密文（明文 JSON 加密后落库）
     */
    private String headersCiphertext;

    /**
     * Stdio 环境变量密文（明文 JSON 加密后落库）
     */
    private String envCiphertext;

    /**
     * 请求超时秒数
     */
    private Integer timeoutSeconds;

    /**
     * 启用状态（ACTIVE / DISABLED）
     */
    private String status;

    /**
     * 最近一次连接测试结果（ok/failed，可为 null）
     */
    private String lastTestStatus;

    /**
     * 最近一次连接测试消息（可为 null）
     */
    private String lastTestMessage;

    /**
     * 最近一次连接测试时间（可为 null）
     */
    private OffsetDateTime lastTestedAt;
}
