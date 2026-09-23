package xin.v5ai.nb.mcp.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * v5ai.mcp 配置项。
 *
 * @param stdioCommandWhitelist Stdio 命令白名单；为空表示开发模式不限制（生产环境必须配置）
 */
@Data
@ConfigurationProperties(prefix = "v5ai.mcp")
public class McpRuntimeProperties {

    private List<String> stdioCommandWhitelist = new ArrayList<>();
}
