package xin.v5ai.nb.mcp;

/**
 * Tool 权限策略：发现 Tool 时决定默认权限。
 * 只读 Tool 默认 ALLOW，可写 Tool 默认 APPROVE（需人工审批，运行时先发事件再放行）。
 */
public final class McpToolPermissionPolicy {

    private McpToolPermissionPolicy() {
    }

    public static McpToolPermission defaultPermission(McpToolInfo info) {
        return defaultPermission(info != null && info.readOnlyHint());
    }

    public static McpToolPermission defaultPermission(boolean readOnly) {
        return readOnly ? McpToolPermission.ALLOW : McpToolPermission.APPROVE;
    }
}
