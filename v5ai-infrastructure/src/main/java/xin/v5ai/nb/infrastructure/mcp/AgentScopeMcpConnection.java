package xin.v5ai.nb.infrastructure.mcp;

import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.core.tool.mcp.McpTool;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xin.v5ai.nb.mcp.McpConnection;
import xin.v5ai.nb.mcp.McpToolCallResult;
import xin.v5ai.nb.mcp.McpToolInfo;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 基于 AgentScope {@link McpClientWrapper} 的平台连接实现：
 * 内部负责 MCP 协议初始化、Tool 缓存与调用，外部暴露平台 {@link McpConnection}。
 */
public class AgentScopeMcpConnection implements McpConnection {
    private static final Logger log = LoggerFactory.getLogger(AgentScopeMcpConnection.class);

    private final McpClientWrapper wrapper;
    private volatile boolean initialized;

    public AgentScopeMcpConnection(McpClientWrapper wrapper) {
        this.wrapper = wrapper;
    }

    @Override
    public List<McpToolInfo> listTools() {
        ensureInitialized();
        var tools = wrapper.listTools().blockOptional().orElse(List.of());
        return tools.stream()
                .map(tool -> new McpToolInfo(
                        tool.name(),
                        tool.description(),
                        convertSchema(tool),
                        isReadOnlyHint(tool)))
                .toList();
    }

    @Override
    public McpToolCallResult callTool(String toolName, Map<String, Object> arguments) {
        try {
            ensureInitialized();
            var result = wrapper.callTool(toolName, arguments == null ? Map.of() : arguments).block();
            if (result == null) {
                return McpToolCallResult.failure("MCP tool returned no result: " + toolName);
            }
            var text = extractText(result);
            if (Boolean.TRUE.equals(result.isError())) {
                return McpToolCallResult.failure(text == null || text.isBlank() ? "MCP tool returned error: " + toolName : text);
            }
            return McpToolCallResult.success(text);
        } catch (Exception exception) {
            log.warn("MCP tool '{}' call failed: {}", toolName, exception.getMessage());
            return McpToolCallResult.failure(exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage());
        }
    }

    @Override
    public void close() {
        try {
            wrapper.close();
        } catch (Exception exception) {
            log.debug("MCP client close ignored: {}", exception.getMessage());
        }
    }

    private void ensureInitialized() {
        if (!initialized) {
            wrapper.initialize().blockOptional();
            initialized = true;
        }
    }

    private static Map<String, Object> convertSchema(McpSchema.Tool tool) {
        return McpTool.convertMcpSchemaToParameters(tool.inputSchema(), Collections.emptySet());
    }

    private static boolean isReadOnlyHint(McpSchema.Tool tool) {
        return tool.annotations() != null && Boolean.TRUE.equals(tool.annotations().readOnlyHint());
    }

    private static String extractText(McpSchema.CallToolResult result) {
        if (result.content() == null) {
            return "";
        }
        var sb = new StringBuilder();
        for (var content : result.content()) {
            if (content instanceof McpSchema.TextContent text) {
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append(text.text());
            }
        }
        return sb.toString();
    }
}
