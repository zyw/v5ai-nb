package xin.v5ai.nb.agent;

public class PublishedAgentResolver {
    private final AgentRepository repository;
    public PublishedAgentResolver(AgentRepository repository) { this.repository = repository; }

    /** 运行时正式路径：仅允许已发布 Agent。 */
    public Agent resolve(String agentKey) {
        var agent = repository.findByAgentKey(agentKey);
        if (agent == null) {
            throw new AgentPublishException("agent does not exist: " + agentKey);
        }
        if (agent.status() != AgentStatus.PUBLISHED) {
            throw new AgentPublishException("agent is not published: " + agentKey);
        }
        return agent;
    }

    /**
     * 管理端调试路径：允许草稿（DRAFT）与已发布（PUBLISHED）状态的 Agent 运行，
     * 便于在新建/编辑页直接调试尚未发布的配置；仅禁用（DISABLED）状态拒绝。
     */
    public Agent resolveForDebug(String agentKey) {
        var agent = repository.findByAgentKey(agentKey);
        if (agent == null) {
            throw new AgentPublishException("agent does not exist: " + agentKey);
        }
        if (agent.status() == AgentStatus.DISABLED) {
            throw new AgentPublishException("agent is disabled: " + agentKey);
        }
        return agent;
    }
}
