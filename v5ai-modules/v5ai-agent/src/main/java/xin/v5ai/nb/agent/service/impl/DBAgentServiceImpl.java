package xin.v5ai.nb.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.agent.domain.Agent;
import xin.v5ai.nb.agent.domain.AgentVersion;
import xin.v5ai.nb.agent.mapper.AgentMapper;
import xin.v5ai.nb.agent.mapper.AgentVersionMapper;
import xin.v5ai.nb.agent.service.IAgentDeletionPortService;
import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentDTO;
import xin.v5ai.nb.common.agentscope.core.domain.dto.AgentVersionDTO;
import xin.v5ai.nb.common.agentscope.core.service.AgentService;
import xin.v5ai.nb.common.agentscope.enums.AgentStatus;
import xin.v5ai.nb.common.agentscope.enums.RagCallMode;

import java.util.List;

/**
 * {@link AgentService} 的 MyBatis-Plus 实现（运行时 AgentDTO 解析与管理读写）。
 * 通过 {@link IAgentDeletionPortService} 委托跨模块级联删除（由 v5ai-agent 模块实现）。
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Primary
@Service
public class DBAgentServiceImpl implements AgentService {

    private final AgentMapper agentMapper;
    private final AgentVersionMapper versionMapper;
    private final IAgentDeletionPortService deletionService;

    @Autowired
    public DBAgentServiceImpl(
            AgentMapper agentMapper,
            AgentVersionMapper versionMapper,
            IAgentDeletionPortService deletionService
    ) {
        this.agentMapper = agentMapper;
        this.versionMapper = versionMapper;
        this.deletionService = deletionService;
    }

    /** 兼容构造器：未配置级联删除服务（测试/内存场景），delete 会抛 UnsupportedOperationException。 */
    public DBAgentServiceImpl(AgentMapper agentMapper, AgentVersionMapper versionMapper) {
        this(agentMapper, versionMapper, null);
    }

    @Override
    public AgentDTO findByAgentKey(String agentKey) {
        return toDomain(agentMapper.selectByAgentKey(agentKey));
    }

    @Override
    public void save(AgentDTO application) {
        agentMapper.insert(toEntity(application));
    }

    @Override
    public void update(AgentDTO application) {
        var entity = toEntity(application);
        var existing = agentMapper.selectByAgentKey(application.agentKey());
        if (existing == null) {
            agentMapper.insert(entity);
        } else {
            entity.setId(existing.getId());
            agentMapper.updateById(entity);
        }
    }

    @Override
    public void disable(String agentKey) {
        agentMapper.update(new LambdaUpdateWrapper<Agent>()
                .eq(xin.v5ai.nb.agent.domain.Agent::getAgentKey, agentKey)
                .set(xin.v5ai.nb.agent.domain.Agent::getStatus, AgentStatus.DISABLED.name()));
    }

    @Override
    public void delete(String agentKey) {
        if (deletionService == null) {
            throw new UnsupportedOperationException("agent deletion is not configured");
        }
        deletionService.delete(agentKey);
    }

    @Override
    public List<AgentDTO> list() {
        return agentMapper.selectList(null).stream()
                .map(DBAgentServiceImpl::toDomain)
                .toList();
    }

    @Override
    public long nextVersion(String agentKey) {
        Long version = versionMapper.nextVersion(agentKey);
        return version == null ? 1L : version;
    }

    @Override
    public void saveVersion(AgentVersionDTO version) {
        var entity = new xin.v5ai.nb.agent.domain.AgentVersion();
        entity.setAgentKey(version.agentKey());
        entity.setVersion(version.version());
        entity.setSnapshotJson(version.snapshotJson());
        entity.setDescription(version.description());
        versionMapper.insert(entity);
    }

    @Override
    public List<AgentVersionDTO> listVersions(String agentKey) {
        return versionMapper.selectList(new LambdaQueryWrapper<AgentVersion>()
                        .eq(xin.v5ai.nb.agent.domain.AgentVersion::getAgentKey, agentKey)
                        .orderByDesc(xin.v5ai.nb.agent.domain.AgentVersion::getVersion))
                .stream()
                .map(entity -> new AgentVersionDTO(entity.getAgentKey(), entity.getVersion(),
                        entity.getSnapshotJson(), entity.getDescription()))
                .toList();
    }

    @Override
    public void publish(String agentKey, long version) {
        agentMapper.update(new LambdaUpdateWrapper<xin.v5ai.nb.agent.domain.Agent>()
                .eq(xin.v5ai.nb.agent.domain.Agent::getAgentKey, agentKey)
                .set(xin.v5ai.nb.agent.domain.Agent::getStatus, AgentStatus.PUBLISHED.name())
                .set(xin.v5ai.nb.agent.domain.Agent::getPublishedVersion, version));
    }

    private static xin.v5ai.nb.agent.domain.Agent toEntity(AgentDTO application) {
        var entity = new xin.v5ai.nb.agent.domain.Agent();
        entity.setAgentKey(application.agentKey());
        entity.setName(application.name());
        entity.setDescription(application.description());
        entity.setStatus(application.status().name());
        entity.setModelId(application.modelId());
        entity.setPublishedVersion(application.publishedVersion());
        entity.setSystemPrompt(application.systemPrompt());
        entity.setAvatar(application.avatar());
        entity.setGreeting(application.greeting());
        entity.setPresetQuestions(application.presetQuestions());
        entity.setMemoryEnabled(application.memoryEnabled());
        entity.setMcpEnabled(application.mcpEnabled());
        entity.setSkillEnabled(application.skillEnabled());
        entity.setWebSearchEnabled(application.webSearchEnabled());
        entity.setRagEnabled(application.ragEnabled());
        entity.setRagCallMode(application.ragCallMode());
        return entity;
    }

    private static AgentDTO toDomain(xin.v5ai.nb.agent.domain.Agent entity) {
        if (entity == null) {
            return null;
        }
        return new AgentDTO(entity.getAgentKey(), entity.getName(), entity.getDescription(),
                AgentStatus.valueOf(entity.getStatus()),
                entity.getModelId(), entity.getPublishedVersion(), entity.getSystemPrompt(),
                entity.getAvatar(), entity.getGreeting(), entity.getPresetQuestions(),
                Boolean.TRUE.equals(entity.getMemoryEnabled()),
                Boolean.TRUE.equals(entity.getMcpEnabled()),
                Boolean.TRUE.equals(entity.getSkillEnabled()),
                Boolean.TRUE.equals(entity.getWebSearchEnabled()),
                Boolean.TRUE.equals(entity.getRagEnabled()),
                entity.getRagCallMode() == null ? RagCallMode.FORCED.value() : entity.getRagCallMode(),
                entity.getSecondaryModelId(),
                // 缺省 true：该列加入之前引用一律展示，null 不能被当作「不展示」
                !Boolean.FALSE.equals(entity.getShowCitations()));
    }
}
