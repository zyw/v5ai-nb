package xin.v5ai.nb.agent.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xin.v5ai.nb.agent.mapper.AgentCleanupMapper;
import xin.v5ai.nb.agent.service.IAgentDeletionPortService;

/**
 * AgentDTO 级联删除实现：全部删除逻辑下沉 {@link AgentCleanupMapper}（XML SQL），
 * 按依赖顺序执行——FK 子表（子查询定位）→ 直连 agent_key 表 → 主表。
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentDeletionServiceImpl implements IAgentDeletionPortService {

    private final AgentCleanupMapper cleanupMapper;

    @Override
    @Transactional
    public void delete(String agentKey) {
        // 1) FK 子表（子查询定位）
        cleanupMapper.deleteRunEvents(agentKey);
        cleanupMapper.deleteMcpToolCalls(agentKey);
        cleanupMapper.deleteAgentStates(agentKey);
        // 2) 直连 agent_key 表
        cleanupMapper.deleteAgentVersions(agentKey);
        cleanupMapper.deleteAgentKnowledgeBindings(agentKey);
        cleanupMapper.deleteAgentMcpBindings(agentKey);
        cleanupMapper.deleteAgentSkillBindings(agentKey);
        cleanupMapper.deleteApiKeyBindings(agentKey);
        cleanupMapper.deleteOrphanApiKeys();
        cleanupMapper.deleteAppQuotas(agentKey);
        cleanupMapper.deleteAppUsages(agentKey);
        cleanupMapper.deleteModelUsages(agentKey);
        cleanupMapper.deleteRuns(agentKey);
        cleanupMapper.deleteMessages(agentKey);
        cleanupMapper.deleteConversationSummaries(agentKey);
        cleanupMapper.deleteConversations(agentKey);
        // 3) 主表
        cleanupMapper.deleteAgentByKey(agentKey);
    }
}
