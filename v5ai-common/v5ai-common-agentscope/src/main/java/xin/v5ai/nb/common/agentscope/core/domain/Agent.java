package xin.v5ai.nb.common.agentscope.core.domain;

import xin.v5ai.nb.common.agentscope.enums.AgentStatus;
import xin.v5ai.nb.common.agentscope.enums.RagCallMode;

/**
 * Agent 领域契约：运行时所需的 Agent 完整配置（发布态快照），
 * 含基础信息、绑定模型、系统提示词、展示信息与能力开关。
 *
 * @param agentKey          Agent 对外运行标识（唯一）
 * @param name              Agent 名称
 * @param description       Agent 描述
 * @param status            状态（DRAFT / PUBLISHED / DISABLED）
 * @param modelId           绑定的模型 ID
 * @param publishedVersion  已发布版本号；未发布时为 null
 * @param systemPrompt      系统提示词
 * @param avatar            头像
 * @param greeting          开场白
 * @param presetQuestions   预设问题（JSON 数组字符串）
 * @param memoryEnabled     是否启用会话记忆
 * @param mcpEnabled        是否启用 MCP 工具
 * @param skillEnabled      是否启用 Skill
 * @param webSearchEnabled  是否启用联网搜索
 * @param ragEnabled        是否启用 RAG 知识库检索
 * @param ragCallMode       RAG 调用方式（{@code 1=智能/2=强制}，仅在 {@code ragEnabled} 开启时生效）
 */
public record Agent(
        String agentKey,
        String name,
        String description,
        AgentStatus status,
        Long modelId,
        Long publishedVersion,
        String systemPrompt,
        String avatar,
        String greeting,
        String presetQuestions,
        boolean memoryEnabled,
        boolean mcpEnabled,
        boolean skillEnabled,
        boolean webSearchEnabled,
        boolean ragEnabled,
        int ragCallMode
) {
    /**
     * 便捷构造器：未设置展示与能力开关字段，使用默认值
     * （字符串为 null、开关为 false、RAG 调用方式为强制 2）。
     */
    public Agent(String agentKey, String name, String description, AgentStatus status,
                 Long modelId, Long publishedVersion, String systemPrompt) {
        this(agentKey, name, description, status, modelId, publishedVersion, systemPrompt,
                null, null, null, false, false, false, false, false, RagCallMode.FORCED.value());
    }

    /**
     * 便捷构造器：未设置系统提示词及展示/能力字段。
     */
    public Agent(String agentKey, String name, String description, AgentStatus status,
                 Long modelId, Long publishedVersion) {
        this(agentKey, name, description, status, modelId, publishedVersion, null);
    }
}
