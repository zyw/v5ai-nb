package xin.v5ai.nb.infrastructure.agent;

import org.apache.ibatis.annotations.Delete;
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
    @Delete("DELETE FROM v5ai_agent_model WHERE agent_id IN (SELECT id FROM v5ai_agent WHERE agent_key = #{agentKey})")
    int deleteAgentModels(@Param("agentKey") String agentKey);

    /** run_event 无 agent_key 列，按 run_id 子查询。 */
    @Delete("DELETE FROM v5ai_run_event WHERE run_id IN (SELECT id FROM v5ai_run WHERE agent_key = #{agentKey})")
    int deleteRunEvents(@Param("agentKey") String agentKey);

    /** MCP 工具调用审计无 agent_key 列，按 run_id 子查询。 */
    @Delete("DELETE FROM v5ai_mcp_tool_call WHERE run_id IN (SELECT id FROM v5ai_run WHERE agent_key = #{agentKey})")
    int deleteMcpToolCalls(@Param("agentKey") String agentKey);

    /** agent_state 无 agent_key 列，按 conversation_id 子查询。 */
    @Delete("DELETE FROM v5ai_agent_state WHERE conversation_id IN (SELECT id FROM v5ai_conversation WHERE agent_key = #{agentKey})")
    int deleteAgentStates(@Param("agentKey") String agentKey);
}
