package xin.v5ai.nb.mcp.core;

import xin.v5ai.nb.common.agentscope.core.domain.McpToolInfo;
import xin.v5ai.nb.common.agentscope.enums.McpToolPermission;

/**
 * Tool 权限策略：在发现 Tool 时决定其默认权限。
 * 规则：只读 Tool 默认 {@link McpToolPermission#ALLOW}（直接放行）；
 * 可写 Tool 默认 {@link McpToolPermission#APPROVE}（需人工审批，运行时先发事件再放行）。
 */
public final class McpToolPermissionPolicy {

    /** 工具类，禁止实例化 */
    private McpToolPermissionPolicy() {
    }

    /**
     * 依据 Tool 元数据决定默认权限（内部取 readOnlyHint）。
     *
     * @param info Tool 元数据
     * @return 默认权限
     */
    public static McpToolPermission defaultPermission(McpToolInfo info) {
        return defaultPermission(info != null && info.readOnlyHint());
    }

    /**
     * 依据只读标记决定默认权限。
     *
     * @param readOnly 是否只读
     * @return 只读为 ALLOW，否则为 APPROVE
     */
    public static McpToolPermission defaultPermission(boolean readOnly) {
        return readOnly ? McpToolPermission.ALLOW : McpToolPermission.APPROVE;
    }
}
