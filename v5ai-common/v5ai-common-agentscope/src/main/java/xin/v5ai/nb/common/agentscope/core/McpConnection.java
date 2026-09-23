package xin.v5ai.nb.common.agentscope.core;

import xin.v5ai.nb.common.agentscope.core.domain.vo.McpToolCallVo;
import xin.v5ai.nb.common.agentscope.core.domain.McpToolInfo;

import java.util.List;
import java.util.Map;

/**
 * 平台对 MCP Server 的连接抽象：连接测试、Tool 发现与运行时 Tool 调用统一走该接口。
 * 实现（如 AgentScope/MCP SDK）放在基础设施层，本模块不依赖具体传输库。
 */
public interface McpConnection extends AutoCloseable {

    /** 连接并列出服务器提供的全部 Tool（未连接时内部完成初始化）。 */
    List<McpToolInfo> listTools();

    /** 调用指定 Tool。失败不抛异常，而是返回 ok=false 的结果。 */
    McpToolCallVo callTool(String toolName, Map<String, Object> arguments);

    /** 释放连接（幂等）。 */
    @Override
    void close();
}
