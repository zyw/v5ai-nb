package xin.v5ai.nb.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xin.v5ai.nb.agent.domain.Agent;
import xin.v5ai.nb.agent.domain.AgentVersion;
import xin.v5ai.nb.agent.domain.bo.AgentBo;
import xin.v5ai.nb.agent.domain.vo.AgentVersionVo;
import xin.v5ai.nb.agent.domain.vo.AgentVo;
import xin.v5ai.nb.agent.mapper.AgentMapper;
import xin.v5ai.nb.agent.mapper.AgentVersionMapper;
import xin.v5ai.nb.agent.service.IAgentDeletionPortService;
import xin.v5ai.nb.agent.service.IAgentService;
import xin.v5ai.nb.common.agentscope.core.exception.AgentPublishException;
import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.core.utils.StringUtils;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.mybatis.core.query.QueryBuilder;

import java.util.List;
import java.util.function.Predicate;

/**
 * Agent 管理服务实现类。
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements IAgentService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    /** RAG 调用方式默认值：强制调用，兼容存量 Agent 行为。 */
    private static final int RAG_CALL_MODE_FORCED = 2;

    private final AgentMapper agentMapper;
    private final AgentVersionMapper versionMapper;
    private final Predicate<Long> modelEnabled;
    private final IAgentDeletionPortService deletionPort;

    @Override
    public PageResult<AgentVo> queryPageList(AgentBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<Agent> lqw = buildQueryWrapper(bo);
        IPage<AgentVo> page = agentMapper.selectVoPage(pageQuery.build(), lqw);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    public List<AgentVo> queryList(AgentBo bo) {
        return agentMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public AgentVo getAgent(String agentKey) {
        return toVo(requireAgent(agentKey));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentVo createAgent(AgentBo bo) {
        if (bo == null || bo.getAgentKey() == null || bo.getAgentKey().isBlank()
                || bo.getName() == null || bo.getName().isBlank()
                || bo.getModelId() == null) {
            throw new IllegalArgumentException("agent key, name and model are required");
        }
        if (agentMapper.selectByAgentKey(bo.getAgentKey()) != null) {
            throw new IllegalArgumentException("agent already exists: " + bo.getAgentKey());
        }
        var agent = new Agent();
        agent.setAgentKey(bo.getAgentKey().trim());
        agent.setName(bo.getName().trim());
        agent.setDescription(bo.getDescription());
        agent.setStatus(STATUS_DRAFT);
        agent.setModelId(bo.getModelId());
        agent.setSystemPrompt(bo.getSystemPrompt());
        agent.setAvatar(bo.getAvatar());
        agent.setGreeting(bo.getGreeting());
        agent.setPresetQuestions(bo.getPresetQuestions());
        agent.setMemoryEnabled(Boolean.TRUE.equals(bo.getMemoryEnabled()));
        agent.setMcpEnabled(Boolean.TRUE.equals(bo.getMcpEnabled()));
        agent.setSkillEnabled(Boolean.TRUE.equals(bo.getSkillEnabled()));
        agent.setWebSearchEnabled(Boolean.TRUE.equals(bo.getWebSearchEnabled()));
        agent.setRagEnabled(Boolean.TRUE.equals(bo.getRagEnabled()));
        agent.setRagCallMode(bo.getRagCallMode() == null ? RAG_CALL_MODE_FORCED : bo.getRagCallMode());
        agentMapper.insert(agent);
        return toVo(agent);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentVo updateAgent(String agentKey, AgentBo bo) {
        var existing = requireAgent(agentKey);
        var update = new Agent();
        update.setId(existing.getId());
        update.setAgentKey(existing.getAgentKey());
        update.setName(bo == null || bo.getName() == null || bo.getName().isBlank()
                ? existing.getName() : bo.getName().trim());
        update.setDescription(bo == null || bo.getDescription() == null
                ? existing.getDescription() : bo.getDescription());
        update.setStatus(existing.getStatus());
        update.setModelId(bo == null || bo.getModelId() == null ? existing.getModelId() : bo.getModelId());
        update.setPublishedVersion(existing.getPublishedVersion());
        update.setSystemPrompt(bo == null || bo.getSystemPrompt() == null
                ? existing.getSystemPrompt() : bo.getSystemPrompt());
        update.setAvatar(bo == null || bo.getAvatar() == null ? existing.getAvatar() : bo.getAvatar());
        update.setGreeting(bo == null || bo.getGreeting() == null ? existing.getGreeting() : bo.getGreeting());
        update.setPresetQuestions(bo == null || bo.getPresetQuestions() == null
                ? existing.getPresetQuestions() : bo.getPresetQuestions());
        update.setMemoryEnabled(bo == null || bo.getMemoryEnabled() == null
                ? existing.getMemoryEnabled() : bo.getMemoryEnabled());
        update.setMcpEnabled(bo == null || bo.getMcpEnabled() == null
                ? existing.getMcpEnabled() : bo.getMcpEnabled());
        update.setSkillEnabled(bo == null || bo.getSkillEnabled() == null
                ? existing.getSkillEnabled() : bo.getSkillEnabled());
        update.setWebSearchEnabled(bo == null || bo.getWebSearchEnabled() == null
                ? existing.getWebSearchEnabled() : bo.getWebSearchEnabled());
        update.setRagEnabled(bo == null || bo.getRagEnabled() == null
                ? existing.getRagEnabled() : bo.getRagEnabled());
        update.setRagCallMode(bo == null || bo.getRagCallMode() == null
                ? (existing.getRagCallMode() == null ? RAG_CALL_MODE_FORCED : existing.getRagCallMode())
                : bo.getRagCallMode());
        agentMapper.updateById(update);
        return toVo(requireAgent(agentKey));
    }

    @Override
    public void disable(String agentKey) {
        var existing = requireAgent(agentKey);
        var update = new Agent();
        update.setId(existing.getId());
        update.setStatus(STATUS_DISABLED);
        agentMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String agentKey) {
        requireAgent(agentKey);
        deletionPort.delete(agentKey);
    }

    @Override
    public List<AgentVersionVo> listVersions(String agentKey) {
        requireAgent(agentKey);
        return versionMapper.selectVersionsByAgentKey(agentKey).stream()
                .map(this::toVersionVo)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(String agentKey, String description) {
        var agent = requireAgent(agentKey);
        if (agent.getModelId() == null || !modelEnabled.test(agent.getModelId())) {
            throw new AgentPublishException("agent requires an enabled model before publishing");
        }
        long version = nextVersion(agentKey);
        var snapshot = new AgentVersion();
        snapshot.setAgentKey(agentKey);
        snapshot.setVersion(version);
        snapshot.setSnapshotJson(snapshot(agent));
        snapshot.setDescription(description);
        versionMapper.insert(snapshot);
        var update = new Agent();
        update.setId(agent.getId());
        update.setStatus(STATUS_PUBLISHED);
        update.setPublishedVersion(version);
        agentMapper.updateById(update);
    }

    /**
     * 构造 Agent 列表查询条件。
     */
    private LambdaQueryWrapper<Agent> buildQueryWrapper(AgentBo bo) {
        // name / agentKey 是既有查询契约（大小写敏感），保留原语义；keyword 是前端唯一入口。
        return QueryBuilder.lambda(Agent.class)
                .likeIfText(Agent::getName, bo.getName())
                .likeIfText(Agent::getAgentKey, bo.getAgentKey())
                .eqIfText(Agent::getStatus, bo.getStatus())
                .nested(StringUtils.isNotBlank(bo.getKeyword()), w -> w.like(Agent::getName, bo.getKeyword()).or().like(Agent::getAgentKey, bo.getKeyword()))
                .orderByAsc(Agent::getId)
                .build();
    }

    private Agent requireAgent(String agentKey) {
        var agent = agentKey == null ? null : agentMapper.selectByAgentKey(agentKey);
        if (agent == null) {
            throw new IllegalArgumentException("agent does not exist: " + agentKey);
        }
        return agent;
    }

    private long nextVersion(String agentKey) {
        Long version = versionMapper.nextVersion(agentKey);
        return version == null ? 1L : version;
    }

    /**
     * 实体转 VO（显式映射，便于单测）。
     */
    private AgentVo toVo(Agent agent) {
        var vo = new AgentVo();
        vo.setId(agent.getId());
        vo.setAgentKey(agent.getAgentKey());
        vo.setName(agent.getName());
        vo.setDescription(agent.getDescription());
        vo.setStatus(agent.getStatus());
        vo.setModelId(agent.getModelId());
        vo.setPublishedVersion(agent.getPublishedVersion());
        vo.setSystemPrompt(agent.getSystemPrompt());
        vo.setAvatar(agent.getAvatar());
        vo.setGreeting(agent.getGreeting());
        vo.setPresetQuestions(agent.getPresetQuestions());
        vo.setMemoryEnabled(agent.getMemoryEnabled());
        vo.setMcpEnabled(agent.getMcpEnabled());
        vo.setSkillEnabled(agent.getSkillEnabled());
        vo.setWebSearchEnabled(agent.getWebSearchEnabled());
        vo.setRagEnabled(agent.getRagEnabled());
        vo.setRagCallMode(agent.getRagCallMode());
        vo.setCreatedAt(agent.getCreatedAt());
        vo.setUpdatedAt(agent.getUpdatedAt());
        return vo;
    }

    private AgentVersionVo toVersionVo(AgentVersion version) {
        var vo = new AgentVersionVo();
        vo.setId(version.getId());
        vo.setAgentKey(version.getAgentKey());
        vo.setVersion(version.getVersion());
        vo.setSnapshotJson(version.getSnapshotJson());
        vo.setDescription(version.getDescription());
        vo.setCreatedAt(version.getCreatedAt());
        return vo;
    }

    /**
     * 生成发布快照 JSON（与 v5ai_agent_version.snapshot_json 的既有格式保持一致）。
     */
    private String snapshot(Agent agent) {
        return """
                {"agentKey":"%s","name":"%s","description":"%s","modelId":%d,"systemPrompt":"%s","avatar":"%s","greeting":"%s","presetQuestions":"%s","memoryEnabled":%b,"mcpEnabled":%b,"skillEnabled":%b,"webSearchEnabled":%b,"ragEnabled":%b,"ragCallMode":%d}
                """.formatted(
                jsonEscape(agent.getAgentKey()),
                jsonEscape(agent.getName()),
                jsonEscape(agent.getDescription()),
                agent.getModelId(),
                jsonEscape(agent.getSystemPrompt()),
                jsonEscape(agent.getAvatar()),
                jsonEscape(agent.getGreeting()),
                jsonEscape(agent.getPresetQuestions()),
                Boolean.TRUE.equals(agent.getMemoryEnabled()),
                Boolean.TRUE.equals(agent.getMcpEnabled()),
                Boolean.TRUE.equals(agent.getSkillEnabled()),
                Boolean.TRUE.equals(agent.getWebSearchEnabled()),
                Boolean.TRUE.equals(agent.getRagEnabled()),
                agent.getRagCallMode() == null ? RAG_CALL_MODE_FORCED : agent.getRagCallMode()).trim();
    }

    private static String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
