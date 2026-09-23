package xin.v5ai.nb.common.agentscope.enums;

/**
 * MCP 传输类型：第一阶段支持 Streamable HTTP、SSE 与 Stdio 三种。
 */
public enum McpTransportType {
    /** 无状态流式 HTTP（MCP 2025-03-26+ 推荐） */
    STREAMABLE_HTTP,
    /** 有状态 HTTP + Server-Sent Events */
    SSE,
    /** 本地子进程（命令 + 参数 + 环境变量） */
    STDIO
}
