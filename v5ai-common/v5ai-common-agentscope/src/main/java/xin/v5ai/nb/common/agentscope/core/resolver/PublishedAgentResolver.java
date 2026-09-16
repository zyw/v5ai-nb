package xin.v5ai.nb.common.agentscope.core.resolver;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentDTO;
import xin.v5ai.nb.common.agentscope.core.exception.AgentPublishException;
import xin.v5ai.nb.common.agentscope.core.service.AgentService;
import xin.v5ai.nb.common.agentscope.enums.AgentStatus;
import xin.v5ai.nb.common.agentscope.enums.RagCallMode;

public class PublishedAgentResolver {

    private final AgentService agentService;

    public PublishedAgentResolver(AgentService agentService) {
        this.agentService = agentService;
    }

    /**
     * 运行时正式路径：仅允许已发布 AgentDTO，并按「发布版本快照」执行——
     * 配置（模型 / 系统提示词 / 能力开关 / 展示字段）取发布时固化的快照，
     * 而非实时编辑行，从而保证线上行为 = 发布时的内容。
     */
    public AgentDTO resolve(String agentKey) {
        var agent = agentService.findByAgentKey(agentKey);
        if (agent == null) {
            throw new AgentPublishException("agent does not exist: " + agentKey);
        }
        if (agent.status() != AgentStatus.PUBLISHED) {
            throw new AgentPublishException("agent is not published: " + agentKey);
        }
        if (agent.publishedVersion() == null) {
            throw new AgentPublishException("agent has no published version: " + agentKey);
        }
        long version = agent.publishedVersion();
        var snapshot = agentService.listVersions(agentKey).stream()
                .filter(v -> v.version() == version)
                .findFirst()
                .orElseThrow(() -> new AgentPublishException(
                        "agent published version snapshot missing: " + agentKey));
        return fromSnapshot(agent.agentKey(), version, snapshot.snapshotJson());
    }

    /**
     * 从发布快照 JSON 还原运行时 AgentDTO 配置：状态恒为 PUBLISHED，
     * publishedVersion 取快照版本号（快照 JSON 不含 status/publishedVersion，在此补齐）。
     */
    private static AgentDTO fromSnapshot(String agentKey, long version, String snapshotJson) {
        JSONObject obj = JSONUtil.parseObj(snapshotJson);
        return new AgentDTO(
                agentKey,
                obj.getStr("name"),
                obj.getStr("description"),
                AgentStatus.PUBLISHED,
                obj.getLong("modelId"),
                version,
                obj.getStr("systemPrompt"),
                obj.getStr("avatar"),
                obj.getStr("greeting"),
                obj.getStr("presetQuestions"),
                obj.getBool("memoryEnabled", false),
                obj.getBool("mcpEnabled", false),
                obj.getBool("skillEnabled", false),
                obj.getBool("webSearchEnabled", false),
                obj.getBool("ragEnabled", false),
                obj.getInt("ragCallMode", RagCallMode.FORCED.value()));
    }

    /**
     * 管理端调试路径：允许草稿（DRAFT）与已发布（PUBLISHED）状态的 AgentDTO 运行，
     * 便于在新建/编辑页直接调试尚未发布的配置；仅禁用（DISABLED）状态拒绝。
     */
    public AgentDTO resolveForDebug(String agentKey) {
        var agent = agentService.findByAgentKey(agentKey);
        if (agent == null) {
            throw new AgentPublishException("agent does not exist: " + agentKey);
        }
        if (agent.status() == AgentStatus.DISABLED) {
            throw new AgentPublishException("agent is disabled: " + agentKey);
        }
        return agent;
    }
}
