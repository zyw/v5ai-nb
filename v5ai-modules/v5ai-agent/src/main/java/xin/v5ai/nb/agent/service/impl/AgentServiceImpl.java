package xin.v5ai.nb.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
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
import xin.v5ai.nb.common.core.exception.ErrorCode;
import xin.v5ai.nb.common.core.exception.V5aiException;
import xin.v5ai.nb.common.core.utils.StringUtils;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.common.mybatis.core.query.QueryBuilder;
import xin.v5ai.nb.model.api.ModelCatalogPort;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * AgentDTO 管理服务实现类。
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
    /** RAG 调用方式默认值：强制调用，兼容存量 AgentDTO 行为。 */
    private static final int RAG_CALL_MODE_FORCED = 2;

    private final AgentMapper agentMapper;
    private final AgentVersionMapper versionMapper;
    private final Predicate<Long> modelEnabled;
    private final IAgentDeletionPortService deletionPort;
    /**
     * 模型目录端口：校验次要模型「存在 + CHAT + 已启用」。
     *
     * <p>用 {@link ObjectProvider} 而非硬依赖：本模块的单测与部分装配场景不提供该实现
     * （与运行时侧对 ModelUsageService 的处理一致）。取不到时跳过次要模型校验——
     * 校验是「提前发现配置错误」的增强，缺了它运行期仍有兜底（回退主模型）。</p>
     */
    private final ObjectProvider<ModelCatalogPort> modelCatalog;

    @Override
    public PageResult<AgentVo> queryPageList(AgentBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<Agent> lqw = buildQueryWrapper(bo);
        IPage<AgentVo> page = agentMapper.selectVoPage(pageQuery.build(), lqw);
        fillModelNames(page.getRecords());
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    public List<AgentVo> queryList(AgentBo bo) {
        List<AgentVo> records = agentMapper.selectVoList(buildQueryWrapper(bo));
        fillModelNames(records);
        return records;
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
        requireUsableSecondaryModel(bo.getSecondaryModelId());
        var agent = new Agent();
        agent.setAgentKey(bo.getAgentKey().trim());
        agent.setName(bo.getName().trim());
        agent.setDescription(bo.getDescription());
        agent.setStatus(STATUS_DRAFT);
        agent.setModelId(bo.getModelId());
        agent.setSecondaryModelId(bo.getSecondaryModelId());
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
        agent.setShowCitations(!Boolean.FALSE.equals(bo.getShowCitations()));
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
        // 次要模型是「可清除」的可选项：这里的 null 表示**清除**（回退绑定的对话模型），
        // 与其它字段的「null 即不改」不同。前端提交的是整个表单、该字段恒在，故不会误清；
        // 若沿用 null=不改，用户一旦选了次要模型就再也回不到「复用对话模型」。
        // 清除必须走单独一条语句——updateById 的 NOT_NULL 策略写不进 null（见 AgentMapper#clearSecondaryModel）。
        boolean clearSecondaryModel = bo != null && bo.getSecondaryModelId() == null;
        if (bo != null && bo.getSecondaryModelId() != null) {
            requireUsableSecondaryModel(bo.getSecondaryModelId());
            update.setSecondaryModelId(bo.getSecondaryModelId());
        }
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
        update.setShowCitations(bo == null || bo.getShowCitations() == null
                ? existing.getShowCitations() : bo.getShowCitations());
        agentMapper.updateById(update);
        if (clearSecondaryModel) {
            agentMapper.clearSecondaryModel(existing.getId());
        }
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
     * 构造 AgentDTO 列表查询条件。
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
        vo.setSecondaryModelId(agent.getSecondaryModelId());
        vo.setShowCitations(!Boolean.FALSE.equals(agent.getShowCitations()));
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
     * 为 Agent 列表批量补齐模型名称，避免前端再发起全量模型查询。
     */
    private void fillModelNames(List<AgentVo> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> modelIds = records.stream()
                .map(AgentVo::getModelId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (modelIds.isEmpty()) {
            return;
        }
        ModelCatalogPort catalog = modelCatalog.getIfAvailable();
        if (catalog == null) {
            return;
        }
        Map<Long, String> modelNames = catalog.findModelNamesByIds(modelIds);
        if (modelNames == null || modelNames.isEmpty()) {
            return;
        }
        records.forEach(record -> record.setModelName(modelNames.get(record.getModelId())));
    }

    /**
     * 生成发布快照 JSON（与 v5ai_agent_version.snapshot_json 的既有格式保持一致）。
     *
     * <p>{@code secondaryModelId} 可空，用 {@link #jsonLong} 输出 {@code null}；{@code showCitations}
     * 缺省为 true（与数据库列默认值、{@code AgentDTO} 的便捷构造器三处一致）。</p>
     */
    private String snapshot(Agent agent) {
        return """
                {"agentKey":"%s","name":"%s","description":"%s","modelId":%d,"secondaryModelId":%s,"systemPrompt":"%s","avatar":"%s","greeting":"%s","presetQuestions":"%s","memoryEnabled":%b,"mcpEnabled":%b,"skillEnabled":%b,"webSearchEnabled":%b,"ragEnabled":%b,"ragCallMode":%d,"showCitations":%b}
                """.formatted(
                jsonEscape(agent.getAgentKey()),
                jsonEscape(agent.getName()),
                jsonEscape(agent.getDescription()),
                agent.getModelId(),
                jsonLong(agent.getSecondaryModelId()),
                jsonEscape(agent.getSystemPrompt()),
                jsonEscape(agent.getAvatar()),
                jsonEscape(agent.getGreeting()),
                jsonEscape(agent.getPresetQuestions()),
                Boolean.TRUE.equals(agent.getMemoryEnabled()),
                Boolean.TRUE.equals(agent.getMcpEnabled()),
                Boolean.TRUE.equals(agent.getSkillEnabled()),
                Boolean.TRUE.equals(agent.getWebSearchEnabled()),
                Boolean.TRUE.equals(agent.getRagEnabled()),
                agent.getRagCallMode() == null ? RAG_CALL_MODE_FORCED : agent.getRagCallMode(),
                !Boolean.FALSE.equals(agent.getShowCitations())).trim();
    }

    /**
     * 可空 Long 的 JSON 字面量：{@code null} 输出 {@code null}，否则输出数字。
     *
     * <p>不要用 {@code %d} 直接格式化可空 Long——它在 null 时会输出不带引号的字面量 {@code null}
     * （碰巧合法），但这一行为依赖 Formatter 的实现细节、且参数顺序错位时毫无提示。</p>
     */
    private static String jsonLong(Long value) {
        return value == null ? "null" : value.toString();
    }

    /**
     * 校验次要模型可用于内部调用：存在 + CHAT 类型 + 已启用。为 null 表示不配置，直接通过。
     *
     * <p><b>为什么必须在这里拦</b>：类型/启用状态不对时，运行期异常发生在
     * {@code AgentScopeModelFactory}，而调用方（会话标题改写、会话摘要压缩）是
     * fire-and-forget 且只打 debug 日志——用户侧看不到任何报错，只会发现「标题不再被改写、
     * 摘要不再更新」。保存时报错是唯一能被使用者看见的时机。</p>
     */
    private void requireUsableSecondaryModel(Long secondaryModelId) {
        if (secondaryModelId == null) {
            return;
        }
        var catalog = modelCatalog.getIfAvailable();
        if (catalog != null && !catalog.isEnabledChatModel(secondaryModelId)) {
            throw new V5aiException(ErrorCode.INVALID_ARGUMENT,
                    "次要模型不可用：需为已启用的对话（CHAT）模型，modelId=" + secondaryModelId);
        }
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
