package xin.v5ai.nb.mcp.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.common.agentscope.core.StdioCommandPolicy;
import xin.v5ai.nb.mcp.config.properties.McpRuntimeProperties;

import java.io.File;
import java.util.List;

/**
 * Stdio 命令策略：命令可以是 PATH 上的纯可执行文件名（如 {@code node}），
 * 也可以是可执行文件的完整/相对路径（如 {@code D:/nvm/v24.9.0/node.exe}），禁止 {@code ..} 路径穿越。
 * 配置了白名单时：白名单项写成纯文件名则按可执行文件名匹配，写成路径则按完整路径匹配（Windows 忽略大小写）。
 * 白名单为空视为开发模式，仅校验命令形态。
 */
@Slf4j
@Component
public class PropertyStdioCommandPolicy implements StdioCommandPolicy {

    private static final boolean WINDOWS = File.separatorChar == '\\';

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
        if (trimmed.contains("..")) {
            throw new IllegalArgumentException(
                    "stdio command must not contain path traversal '..': " + trimmed);
        }
        if (whitelist.isEmpty() || matchesWhitelist(trimmed)) {
            return;
        }
        throw new IllegalArgumentException(
                "stdio command is not in the allowlist (v5ai.mcp.stdio-command-whitelist): " + trimmed);
    }

    /**
     * 白名单项含路径分隔符时按完整路径匹配，否则按可执行文件名匹配。
     */
    private boolean matchesWhitelist(String command) {
        var commandName = fileName(command);
        var commandPath = normalizePath(command);
        for (String entry : whitelist) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            var allowed = entry.trim();
            boolean matched = containsSeparator(allowed)
                    ? samePath(normalizePath(allowed), commandPath)
                    : samePath(allowed, commandName);
            if (matched) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsSeparator(String value) {
        return value.indexOf('/') >= 0 || value.indexOf('\\') >= 0;
    }

    private static String fileName(String command) {
        var normalized = normalizePath(command);
        var index = normalized.lastIndexOf('/');
        return index < 0 ? normalized : normalized.substring(index + 1);
    }

    /** 统一分隔符，便于比较 Windows 风格的 \\ 与 / 写法。 */
    private static String normalizePath(String value) {
        return value.replace('\\', '/');
    }

    private static boolean samePath(String left, String right) {
        return WINDOWS ? left.equalsIgnoreCase(right) : left.equals(right);
    }
}
