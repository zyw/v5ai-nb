package xin.v5ai.nb.common.agentscope.core.resolver;

import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentDTO;

public interface PublishedAgentResolver {

    /**
     * 运行时正式路径：仅允许已发布 AgentDTO，并按「发布版本快照」执行——
     * 配置（模型 / 系统提示词 / 能力开关 / 展示字段）取发布时固化的快照，
     * 而非实时编辑行，从而保证线上行为 = 发布时的内容。
     */
    AgentDTO resolve(String agentKey);

    /**
     * 管理端调试路径：允许草稿（DRAFT）与已发布（PUBLISHED）状态的 AgentDTO 运行，
     * 便于在新建/编辑页直接调试尚未发布的配置；仅禁用（DISABLED）状态拒绝。
     */
    AgentDTO resolveForDebug(String agentKey);
}
