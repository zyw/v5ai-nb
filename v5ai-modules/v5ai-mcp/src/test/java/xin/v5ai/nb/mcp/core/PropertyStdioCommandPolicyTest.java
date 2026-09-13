package xin.v5ai.nb.mcp.core;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.mcp.config.properties.McpRuntimeProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PropertyStdioCommandPolicyTest {

    private static PropertyStdioCommandPolicy policy(String... whitelist) {
        var properties = new McpRuntimeProperties();
        properties.setStdioCommandWhitelist(List.of(whitelist));
        return new PropertyStdioCommandPolicy(properties);
    }

    @Test
    void rejectsBlankCommand() {
        var policy = policy();
        assertThatThrownBy(() -> policy.validate(null)).hasMessageContaining("required");
        assertThatThrownBy(() -> policy.validate("  ")).hasMessageContaining("required");
    }

    @Test
    void allowsAbsolutePathWhenWhitelistEmpty() {
        var policy = policy();
        assertThatCode(() -> policy.validate("D:/nvm/v24.9.0/node.exe")).doesNotThrowAnyException();
        assertThatCode(() -> policy.validate("D:\\nvm\\v24.9.0\\node.exe")).doesNotThrowAnyException();
        assertThatCode(() -> policy.validate("node")).doesNotThrowAnyException();
    }

    @Test
    void matchesBareWhitelistEntryByFileName() {
        var policy = policy("node.exe");
        assertThatCode(() -> policy.validate("node.exe")).doesNotThrowAnyException();
        assertThatCode(() -> policy.validate("D:/nvm/v24.9.0/node.exe")).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.validate("D:/nvm/v24.9.0/python.exe"))
                .hasMessageContaining("allowlist");
        assertThatThrownBy(() -> policy.validate("python")).hasMessageContaining("allowlist");
    }

    @Test
    void matchesPathWhitelistEntryByFullPath() {
        var policy = policy("D:/nvm/v24.9.0/node.exe");
        assertThatCode(() -> policy.validate("D:/nvm/v24.9.0/node.exe")).doesNotThrowAnyException();
        assertThatCode(() -> policy.validate("D:\\nvm\\v24.9.0\\node.exe")).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.validate("D:/other/node.exe")).hasMessageContaining("allowlist");
        assertThatThrownBy(() -> policy.validate("node.exe")).hasMessageContaining("allowlist");
    }

    @Test
    void rejectsPathTraversal() {
        var policy = policy();
        assertThatThrownBy(() -> policy.validate("D:/nvm/../node.exe"))
                .hasMessageContaining("path traversal");
    }
}
