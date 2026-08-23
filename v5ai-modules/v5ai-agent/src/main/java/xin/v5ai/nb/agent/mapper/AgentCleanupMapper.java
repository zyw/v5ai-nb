package xin.v5ai.nb.agent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Agent 级联删除的专用清理 Mapper：
 * 处理没有 agent_key 直连列、需要通过子查询定位关联行的表
 * （FK 到 v5ai_agent.id / v5ai_run.id / v5ai_conversation.id）。
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
}
