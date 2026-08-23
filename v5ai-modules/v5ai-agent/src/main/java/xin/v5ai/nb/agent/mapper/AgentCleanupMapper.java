package xin.v5ai.nb.agent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Agent 级联删除的专用清理 Mapper（XML 实现见 mapper/AgentCleanupMapper.xml）：
 * <ul>
 *   <li>FK 子表：无 agent_key 直连列，通过子查询定位（v5ai_agent_model / v5ai_run_event /
 *       v5ai_mcp_tool_call / v5ai_agent_state）；</li>
 *   <li>直连表：按 agent_key 直接删除（版本/知识库/MCP/Skill 绑定、API Key、配额/用量、会话/消息/运行）；</li>
 *   <li>主表：v5ai_agent。</li>
 * </ul>
 */
@Mapper
public interface AgentCleanupMapper {

    /** FK 到 v5ai_agent(id)，必须先于主表删除。 */
    int deleteAgentModels(@Param("agentKey") String agentKey);

    /** run_event 无 agent_key 列，按 run_id 子查询。 */
    int deleteRunEvents(@Param("agentKey") String agentKey);

    /** MCP 工具调用审计无 agent_key 列，按 run_id 子查询。 */
    int deleteMcpToolCalls(@Param("agentKey") String agentKey);

    /** agent_state 无 agent_key 列，按 conversation_id 子查询。 */
    int deleteAgentStates(@Param("agentKey") String agentKey);

    /** 直连 agent_key 表：Agent 版本。 */
    int deleteAgentVersions(@Param("agentKey") String agentKey);

    /** 直连 agent_key 表：知识库绑定。 */
    int deleteAgentKnowledgeBindings(@Param("agentKey") String agentKey);

    /** 直连 agent_key 表：MCP Server 绑定。 */
    int deleteAgentMcpBindings(@Param("agentKey") String agentKey);

    /** 直连 agent_key 表：Skill 绑定。 */
    int deleteAgentSkillBindings(@Param("agentKey") String agentKey);

    /** 直连 agent_key 表：Agent API Key。 */
    int deleteApiKeys(@Param("agentKey") String agentKey);

    /** 直连 agent_key 表：应用配额。 */
    int deleteAppQuotas(@Param("agentKey") String agentKey);

    /** 直连 agent_key 表：应用用量。 */
    int deleteAppUsages(@Param("agentKey") String agentKey);

    /** 直连 agent_key 表：模型用量。 */
    int deleteModelUsages(@Param("agentKey") String agentKey);

    /** 直连 agent_key 表：运行记录。 */
    int deleteRuns(@Param("agentKey") String agentKey);

    /** 直连 agent_key 表：消息。 */
    int deleteMessages(@Param("agentKey") String agentKey);

    /** 直连 agent_key 表：会话。 */
    int deleteConversations(@Param("agentKey") String agentKey);

    /** 主表：v5ai_agent（最后删除）。 */
    int deleteAgentByKey(@Param("agentKey") String agentKey);
}
