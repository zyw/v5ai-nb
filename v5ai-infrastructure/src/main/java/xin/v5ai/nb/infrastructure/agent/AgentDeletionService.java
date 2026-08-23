package xin.v5ai.nb.infrastructure.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xin.v5ai.nb.agent.domain.Agent;
import xin.v5ai.nb.agent.domain.AgentVersion;
import xin.v5ai.nb.agent.mapper.AgentMapper;
import xin.v5ai.nb.agent.mapper.AgentVersionMapper;
import xin.v5ai.nb.common.agentscope.core.AgentDeletionPort;
import xin.v5ai.nb.common.agentscope.core.service.AgentMcpBindingService;
import xin.v5ai.nb.common.agentscope.core.service.McpToolCallAuditService;
import xin.v5ai.nb.infrastructure.runtime.AgentStateMapper;
import xin.v5ai.nb.infrastructure.runtime.RuntimeConversationMapper;
import xin.v5ai.nb.infrastructure.runtime.RuntimeMessageMapper;
import xin.v5ai.nb.infrastructure.runtime.RuntimeRunMapper;
import xin.v5ai.nb.platform.domain.ApiKey;
import xin.v5ai.nb.platform.domain.AppQuota;
import xin.v5ai.nb.platform.domain.AppUsage;
import xin.v5ai.nb.platform.domain.ModelUsage;
import xin.v5ai.nb.platform.mapper.ApiKeyMapper;
import xin.v5ai.nb.platform.mapper.AppQuotaMapper;
import xin.v5ai.nb.platform.mapper.AppUsageMapper;
import xin.v5ai.nb.platform.mapper.ModelUsageMapper;
import xin.v5ai.nb.rag.domain.AgentKnowledgeBinding;
import xin.v5ai.nb.rag.mapper.AgentKnowledgeBindingMapper;
import xin.v5ai.nb.skill.domain.AgentSkillBinding;
import xin.v5ai.nb.skill.mapper.AgentSkillBindingMapper;

/**
 * Agent 级联删除：清理 Agent 及其全部关联数据
 * （版本、模型绑定、知识库/MCP/Skill 绑定、API Key、会话/消息/运行/事件、
 * 配额/用量、MCP 工具调用审计），最后删除主表记录。
 *
 * <p>删除顺序遵循外键依赖：先子表（v5ai_agent_model → agent.id、
 * run_event/mcp_tool_call → run.id、agent_state → conversation.id），
 * 再直连表（按 agent_key），最后主表。</p>
 */
@Service
public class AgentDeletionService implements AgentDeletionPort {
    private final AgentMapper agentMapper;
    private final AgentVersionMapper agentVersionMapper;
    private final AgentKnowledgeBindingMapper knowledgeBindingMapper;
    private final AgentMcpBindingService mcpBindingService;
    private final AgentSkillBindingMapper skillBindingMapper;
    private final ApiKeyMapper apiKeyMapper;
    private final AppQuotaMapper appQuotaMapper;
    private final AppUsageMapper appUsageMapper;
    private final ModelUsageMapper modelUsageMapper;
    private final McpToolCallAuditService mcpToolCallAuditService;
    private final RuntimeRunMapper runMapper;
    private final RuntimeMessageMapper messageMapper;
    private final RuntimeConversationMapper conversationMapper;
    private final AgentStateMapper agentStateMapper;
    private final AgentCleanupMapper cleanupMapper;

    public AgentDeletionService(
            AgentMapper agentMapper,
            AgentVersionMapper agentVersionMapper,
            AgentKnowledgeBindingMapper knowledgeBindingMapper,
            AgentMcpBindingService mcpBindingService,
            AgentSkillBindingMapper skillBindingMapper,
            ApiKeyMapper apiKeyMapper,
            AppQuotaMapper appQuotaMapper,
            AppUsageMapper appUsageMapper,
            ModelUsageMapper modelUsageMapper,
            McpToolCallAuditService mcpToolCallAuditService,
            RuntimeRunMapper runMapper,
            RuntimeMessageMapper messageMapper,
            RuntimeConversationMapper conversationMapper,
            AgentStateMapper agentStateMapper,
            AgentCleanupMapper cleanupMapper
    ) {
        this.agentMapper = agentMapper;
        this.agentVersionMapper = agentVersionMapper;
        this.knowledgeBindingMapper = knowledgeBindingMapper;
        this.mcpBindingService = mcpBindingService;
        this.skillBindingMapper = skillBindingMapper;
        this.apiKeyMapper = apiKeyMapper;
        this.appQuotaMapper = appQuotaMapper;
        this.appUsageMapper = appUsageMapper;
        this.modelUsageMapper = modelUsageMapper;
        this.mcpToolCallAuditService = mcpToolCallAuditService;
        this.runMapper = runMapper;
        this.messageMapper = messageMapper;
        this.conversationMapper = conversationMapper;
        this.agentStateMapper = agentStateMapper;
        this.cleanupMapper = cleanupMapper;
    }

    @Override
    @Transactional
    public void delete(String agentKey) {
        // 1) FK 子表（子查询定位）
        cleanupMapper.deleteAgentModels(agentKey);
        cleanupMapper.deleteRunEvents(agentKey);
        cleanupMapper.deleteMcpToolCalls(agentKey);
        cleanupMapper.deleteAgentStates(agentKey);
        // 2) 直连 agent_key 表
        agentVersionMapper.delete(new LambdaQueryWrapper<AgentVersion>()
                .eq(AgentVersion::getAgentKey, agentKey));
        knowledgeBindingMapper.delete(new LambdaQueryWrapper<AgentKnowledgeBinding>()
                .eq(AgentKnowledgeBinding::getAgentKey, agentKey));
//        mcpBindingMapper.delete(new LambdaQueryWrapper<AgentMcpBinding>()
//                .eq(AgentMcpBinding::getAgentKey, agentKey));
        mcpBindingService.delete(agentKey);
        skillBindingMapper.delete(new LambdaQueryWrapper<AgentSkillBinding>()
                .eq(AgentSkillBinding::getAgentKey, agentKey));
        apiKeyMapper.delete(new LambdaQueryWrapper<ApiKey>()
                .eq(ApiKey::getAgentKey, agentKey));
        appQuotaMapper.delete(new LambdaQueryWrapper<AppQuota>()
                .eq(AppQuota::getAgentKey, agentKey));
        appUsageMapper.delete(new LambdaQueryWrapper<AppUsage>()
                .eq(AppUsage::getAgentKey, agentKey));
        modelUsageMapper.delete(new LambdaQueryWrapper<ModelUsage>()
                .eq(ModelUsage::getAgentKey, agentKey));
        runMapper.delete(new LambdaQueryWrapper<xin.v5ai.nb.infrastructure.runtime.RuntimeRunEntity>()
                .eq(xin.v5ai.nb.infrastructure.runtime.RuntimeRunEntity::getAgentKey, agentKey));
        messageMapper.delete(new LambdaQueryWrapper<xin.v5ai.nb.infrastructure.runtime.RuntimeMessageEntity>()
                .eq(xin.v5ai.nb.infrastructure.runtime.RuntimeMessageEntity::getAgentKey, agentKey));
        conversationMapper.delete(new LambdaQueryWrapper<xin.v5ai.nb.infrastructure.runtime.RuntimeConversationEntity>()
                .eq(xin.v5ai.nb.infrastructure.runtime.RuntimeConversationEntity::getAgentKey, agentKey));
        // 3) 主表
        agentMapper.delete(new LambdaQueryWrapper<Agent>()
                .eq(Agent::getAgentKey, agentKey));
    }
}
