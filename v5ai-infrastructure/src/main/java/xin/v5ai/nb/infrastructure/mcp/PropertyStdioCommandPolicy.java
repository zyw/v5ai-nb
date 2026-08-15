package xin.v5ai.nb.infrastructure.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.mcp.StdioCommandPolicy;

import java.util.List;

/**
 * Stdio 命令策略：命令必须是纯可执行文件名（禁止路径分隔符与 .. 穿越），
 * 且（配置了白名单时）必须命中白名单。白名单为空视为开发模式，仅校验命令形态。
 */
@Component
public class PropertyStdioCommandPolicy implements StdioCommandPolicy {
    private static final Logger log = LoggerFactory.getLogger(PropertyStdioCommandPolicy.class);

    private final List<String> whitelist;

    public PropertyStdioCommandPolicy(McpRuntimeProperties properties) {
        this.whitelist = properties.getStdioCommandWhitelist();
        if (this.whitelist.isEmpty()) {
            log.warn("v5ai.mcp.stdio-command-whitelist is empty: stdio commands are not restricted "
                    + "by allowlist (development mode). Set the allowlist in production.");
        }
    }

    @Override
    public void validate(String command) {
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("stdio command is required");
        }
        var trimmed = command.trim();
        if (trimmed.contains("/") || trimmed.contains("\\") || trimmed.contains("..")) {
            throw new IllegalArgumentException(
                    "stdio command must be a plain executable name without path separators: " + trimmed);
        }
        if (!whitelist.isEmpty() && !whitelist.contains(trimmed)) {
            throw new IllegalArgumentException(
                    "stdio command is not in the allowlist (v5ai.mcp.stdio-command-whitelist): " + trimmed);
        }
    }
}
