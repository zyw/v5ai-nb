package xin.v5ai.nb.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xin.v5ai.nb.agent.domain.Agent;
import xin.v5ai.nb.agent.domain.AgentVersion;
import xin.v5ai.nb.agent.mapper.AgentMapper;
import xin.v5ai.nb.agent.mapper.AgentVersionMapper;
import xin.v5ai.nb.agent.service.IAgentDeletionPortService;
import xin.v5ai.nb.common.agentscope.core.service.AgentMcpBindingService;
import xin.v5ai.nb.common.agentscope.core.service.McpToolCallAuditService;
import xin.v5ai.nb.agent.mapper.AgentCleanupMapper;
import xin.v5ai.nb.runtime.core.mapper.AgentStateMapper;
import xin.v5ai.nb.runtime.core.mapper.RuntimeConversationMapper;
import xin.v5ai.nb.runtime.core.mapper.RuntimeMessageMapper;
import xin.v5ai.nb.runtime.core.mapper.RuntimeRunMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentDeletionServiceImpl implements IAgentDeletionPortService {

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
